package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeAnnualInterestCredit
import `in`.growurdhan.model.NativePortfolioMath
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Single-FY owner-reviewed COMPOUND credit. This deliberately does not
 * extrapolate historical government rates, monthly PPF day-5 balances, EPF
 * passbook contributions, or additional deposits. Prior cash coupon support
 * is a different path and never changes this scheme balance.
 */
internal class NativeAnnualInterestCreditRepository(
    private val accounts: NativeAccountRepository
) {
    data class Review(val holdingId: String,
        val input: NativeAnnualInterestCredit.Input,
        val credit: NativeAnnualInterestCredit.Credit,
        val fingerprint: String)
    data class Receipt(val eventId: String, val newBalanceMinor: Long)
    private fun SQLiteDatabase.has(sql: String, vararg p: String): Boolean =
        rawQuery(sql, p).use { it.moveToFirst() }

    private fun snapshot(db: SQLiteDatabase, holdingId: String,
        input: NativeAnnualInterestCredit.Input): Review {
        val source = "native-compound-fy:$holdingId:${input.financialYearEnd}"
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(s: String?) {
            val data = s?.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(data?.size ?: -1).array())
            if (data != null) digest.update(data)
        }
        val info = db.rawQuery("SELECT h.entity_id,h.account_id,h.current_price_minor," +
            "h.last_price_updated,h.price_source,h.needs_review,h.acquired_on," +
            "h.interest_rate_bps,h.maturity_date,i.category,i.sub_category,i.name," +
            "h.details_json " +
            "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
            "WHERE h.holding_id=?", arrayOf(holdingId)).use { c ->
            require(c.moveToFirst()) { "Unknown compound holding" }
            (0 until c.columnCount).map { i ->
                if (c.isNull(i)) null else c.getString(i)
            }.also { require(!c.moveToNext()) }
        }
        info.forEach(::feed)
        // Contributions inside opaque specialist details cannot safely be
        // deduced from the ledger of a single recorded deposit unit.
        require(info[12] == null || info[12] == "{}") {
            "Specialist deposit details require dated contribution reconciliation"
        }
        val owner = requireNotNull(info[0])
        require(db.has("SELECT 1 FROM entity WHERE entity_id=? AND is_active=1", owner))
        info[1]?.let { custodian ->
            require(db.has("SELECT 1 FROM account WHERE account_id=? " +
                "AND entity_id=? AND closed_on IS NULL", custodian, owner))
        }
        require(!db.has("SELECT 1 FROM setting WHERE key=? AND value='1'",
            "native.holding.archived.$holdingId"))
        require(info[5] == "0" && info[9] == "FIXED_INCOME")
        val sub = info[10].orEmpty().uppercase()
        require(when(input.kind) {
            "PPF" -> sub.contains("PPF") || sub.contains("PUBLIC PROVIDENT")
            "EPF" -> sub.contains("EPF") || sub.contains("PROVIDENT FUND")
            "SSY" -> sub.contains("SSY") || sub.contains("SUKANYA")
            else -> false
        }) { "Unrecognized compound instrument" }
        val purchased = LocalDate.parse(requireNotNull(info[6]))
        require(purchased == input.entitledFrom)
        val priceDate = LocalDate.parse(requireNotNull(info[3]))
        val fyStart = LocalDate.of(input.financialYearEnd.year - 1, 4, 1)
        require(!priceDate.isAfter(fyStart)) {
            "Later recorded balance may include unknown deposits/interest"
        }
        require(info[2]!!.toLong() == input.principalMinor && info[4] !in
            setOf("USER_OVERRIDE", "Manual Verified")) {
            "The reviewed opening amount is not the recorded principal"
        }
        if (info[7] != null) require(info[7]!!.toInt() == input.annualRateBps) {
            "Entered rate conflicts with the encrypted contract"
        }
        info[8]?.let { require(!input.financialYearEnd.isAfter(LocalDate.parse(it))) }
        // One fund-unit face-balance only: never treat an equity-style price as
        // the entire deposit or credit every holding unit separately.
        val events = mutableListOf<NativePortfolioMath.Event>()
        db.rawQuery("SELECT ledger_event_id,event_type,trade_date,quantity_scaled," +
            "unit_price_minor,total_amount_minor,charges_minor,supersedes_event_id," +
            "source_ref,notes FROM ledger_event WHERE holding_id=? " +
            "ORDER BY trade_date,ledger_event_id", arrayOf(holdingId)).use { cur ->
            while (cur.moveToNext()) {
                for(i in 0 until cur.columnCount)
                    feed(if (cur.isNull(i)) null else cur.getString(i))
                val kind = cur.getString(1)
                val day = LocalDate.parse(cur.getString(2))
                // Contributions or corrections within the crediting year make
                // the flat-rate full-year calculation unsafe.
                require(day.isBefore(fyStart)) {
                    "In-year ledger activity needs detailed passbook accrual"
                }
                val provenance = if (cur.isNull(8)) "" else cur.getString(8)
                require(provenance != source) { "Already credited this FY" }
                require(!(kind == "INTEREST" && day == input.financialYearEnd)) {
                    "Conflicting manual interest already exists on the FY credit day"
                }
                val eventId = cur.getString(0)
                val provenance = db.rawQuery("SELECT value FROM setting WHERE key=?",
                    arrayOf("native.ledger.cost_source.$eventId")).use { meta ->
                    if (meta.moveToFirst()) meta.getString(0) else null
                }
                feed(provenance)
                // A recorded zero-cost opening is NOT proof that the source
                // principal or tax basis is known.
                val known = provenance == "KNOWN" ||
                    (provenance == null && kind != "OPENING_BALANCE" &&
                        kind != "SNAPSHOT_ADJUSTMENT")
                events += NativePortfolioMath.Event(eventId, owner, holdingId,
                    kind, day.toString(), cur.getLong(3), cur.getLong(4),
                    cur.getLong(5), cur.getLong(6),
                    if (cur.isNull(7)) null else cur.getString(7),
                    knownCost = known)
            }
        }
        val position = NativePortfolioMath.fold(events, "FIXED_INCOME")
        require(position.errors.isEmpty() &&
            position.quantityScaled == NativePortfolioMath.QTY_SCALE &&
            !position.hasUnknownCost) {
            "Unreconciled deposit, lot or quantity; no compounded interest posted"
        }
        val credit = NativeAnnualInterestCredit.calculate(input, LocalDate.now())
        feed(holdingId); feed(input.kind); feed(input.financialYearEnd.toString())
        feed(input.principalMinor.toString()); feed(input.annualRateBps.toString())
        feed(input.entitledFrom.toString()); feed(credit.amountMinor.toString())
        return Review(holdingId, input, credit,
            digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun inspect(email: String, holdingId: String,
        input: NativeAnnualInterestCredit.Input): Review =
        accounts.withUnlockedVault(email) { snapshot(it, holdingId, input) }

    fun approve(email: String, previous: Review, ownerApproved: Boolean): Receipt {
        require(ownerApproved) { "Owner must review annual interest credit" }
        return accounts.withUnlockedVault(email) { db ->
            val current = snapshot(db, previous.holdingId, previous.input)
            require(current == previous) {
                "Encrypted holding or credit evidence changed after review"
            }
            val source = "native-compound-fy:${previous.holdingId}:${current.credit.date}"
            require(!db.has("SELECT 1 FROM ledger_event WHERE source_ref=?", source))
            val holding = db.rawQuery("SELECT h.entity_id,h.account_id,i.name " +
                "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(previous.holdingId)).use { c ->
                check(c.moveToFirst())
                Triple(c.getString(0),
                    if (c.isNull(1)) null else c.getString(1), c.getString(2))
            }
            val id = UUID.randomUUID().toString()
            db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id," +
                "holding_id,holding_name,account_id,event_type,trade_date," +
                "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
                "entered_at,entered_by,source_ref,notes) VALUES(?,?,?,?,?," +
                "'INTEREST',?,0,0,?,0,?,?,?,?)", arrayOf<Any?>(
                id, holding.first, previous.holdingId, holding.third, holding.second,
                current.credit.date, current.credit.amountMinor, Instant.now().toString(),
                "owner-reviewed-fy", source,
                "FY annual credit: explicit principal/rate; not issuer-verified"))
            db.execSQL("UPDATE holding SET current_price_minor=?,last_price_updated=?," +
                "price_source='FY OWNER CONFIRMED' WHERE holding_id=?",
                arrayOf<Any>(current.credit.closingMinor, current.credit.date,
                    previous.holdingId))
            Receipt(id, current.credit.closingMinor)
        }
    }
}
