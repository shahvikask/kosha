package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeInterestPayoutPlanner
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * P22 fixed cash-coupon source path. Never accrues automatically.
 *
 * Owner reviews the actual principal and rate, every calculated due date and
 * amount, and approves a SHA-bound snapshot of this holding's SQLCipher
 * records. Approval rechecks that same source in one encrypted transaction.
 * Interest events are cash inflows, NEVER extra quantity, BUY or an invented
 * price update. No hardcoded scheme rates or historical rate assumptions.
 */
internal class NativeInterestPayoutRepository(private val accounts: NativeAccountRepository) {
    private val planner = NativeInterestPayoutPlanner
    data class Review(val holdingId: String,
        val input: NativeInterestPayoutPlanner.Input,
        val coupons: List<NativeInterestPayoutPlanner.Coupon>,
        val reviewSha256: String)
    data class Receipt(val posted: Int, val eventIds: List<String>)

    private fun SQLiteDatabase.has(sql: String, vararg params: String): Boolean =
        rawQuery(sql, params).use { it.moveToFirst() }

    private fun snapshot(db: SQLiteDatabase, holdingId: String,
        input: NativeInterestPayoutPlanner.Input): Review {
        val info = db.rawQuery("SELECT h.entity_id,h.account_id,h.acquired_on," +
            "h.maturity_date,h.interest_rate_bps,h.coupon_rate_bps,h.needs_review," +
            "i.category,i.sub_category,i.name " +
            "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
            "WHERE h.holding_id=?", arrayOf(holdingId)).use { c ->
                require(c.moveToFirst()) { "Unknown interest holding" }
                (0 until c.columnCount).map { i -> if (c.isNull(i)) null else c.getString(i) }
            }
        require(!db.has("SELECT 1 FROM setting WHERE key=? AND value='1'",
            "native.holding.archived.$holdingId")) { "Archived holding" }
        val owner = requireNotNull(info[0])
        require(db.has("SELECT 1 FROM entity WHERE entity_id=? AND is_active=1", owner)) {
            "Inactive owner"
        }
        info[1]?.let { custody ->
            require(db.has("SELECT 1 FROM account WHERE account_id=? " +
                "AND entity_id=? AND closed_on IS NULL", custody, owner))
        }
        require(info[6] == "0" && input.acquiredOn.toString() == info[2] &&
            ((info[3] == null && input.maturityOn == null) ||
                (info[3] != null && LocalDate.parse(info[3]) == input.maturityOn))) {
            "Unreconciled holding or contract dates changed"
        }
        val sub = (info[8].orEmpty() + " " + info[9].orEmpty()).uppercase()
        when (input.kind) {
            NativeInterestPayoutPlanner.Kind.SGB -> require(
                info[7] == "GOLD_SGB" && (sub.contains("SGB") ||
                    sub.contains("SOVEREIGN")))
            NativeInterestPayoutPlanner.Kind.SCSS -> require(
                info[7] == "FIXED_INCOME" && sub.contains("SCSS"))
            NativeInterestPayoutPlanner.Kind.POMIS -> require(
                info[7] == "FIXED_INCOME" && (sub.contains("POMIS") ||
                    sub.contains("MONTHLY INCOME")))
        }
        val storedRate = if (input.kind == NativeInterestPayoutPlanner.Kind.SGB)
            info[5] else info[4]
        // A user-requested rate cannot silently override an existing
        // recorded contract rate. A missing rate still needs owner attestation.
        if (storedRate != null) require(storedRate.toInt() == input.annualRateBps) {
            "Entered interest rate differs from recorded contract"
        }
        require(!input.asOf.isAfter(LocalDate.now())) { "Future coupons not due" }

        val posted = mutableSetOf<LocalDate>()
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(value: String?) {
            val bytes = value?.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
            if (bytes != null) digest.update(bytes)
        }
        info.forEach(::feed)
        db.rawQuery("SELECT ledger_event_id,event_type,trade_date," +
            "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
            "supersedes_event_id,source_ref,notes FROM ledger_event WHERE holding_id=? " +
            "ORDER BY trade_date,ledger_event_id", arrayOf(holdingId)).use { c ->
            while (c.moveToNext()) {
                for (i in 0 until c.columnCount)
                    feed(if (c.isNull(i)) null else c.getString(i))
                if (c.getString(1) == "INTEREST") {
                    val day = LocalDate.parse(c.getString(2))
                    val sourceRef = if (c.isNull(8)) "" else c.getString(8)
                    if (sourceRef.startsWith("native-coupon:$holdingId:"))
                        posted += day
                }
            }
        }
        // A correction of an already-posted coupon makes the due-date state
        // ambiguous. Never silently recreate or skip altered income; ask the
        // owner to reconcile it manually using immutable event history.
        require(!db.has("SELECT 1 FROM ledger_event c " +
            "JOIN ledger_event p ON p.ledger_event_id=c.supersedes_event_id " +
            "WHERE p.holding_id=? AND p.source_ref LIKE ?",
            holdingId, "native-coupon:$holdingId:%")) {
            "Corrected coupon history requires manual reconciliation"
        }
        val result = planner.plan(input, posted)
        require(result.coupons.size <= 60) {
            "Review smaller due-date batches before publishing"
        }
        // Other/manual income on these dates is ambiguous. A human must
        // reconcile it, not automatically duplicate the same cash receipt.
        for (coupon in result.coupons) {
            require(!db.has("SELECT 1 FROM ledger_event " +
                "WHERE holding_id=? AND event_type='INTEREST' AND trade_date=?",
                holdingId, coupon.date.toString())) {
                "Existing interest on scheduled date requires manual reconciliation"
            }
        }
        feed(holdingId)
        feed(input.kind.name)
        feed(input.principalMinor.toString())
        feed(input.annualRateBps.toString())
        feed(input.asOf.toString())
        feed(input.maturityOn?.toString())
        result.coupons.forEach { feed(it.date.toString()); feed(it.amountMinor.toString()) }
        return Review(holdingId, input, result.coupons,
            digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun inspect(email: String, holdingId: String,
        input: NativeInterestPayoutPlanner.Input): Review =
        accounts.withUnlockedVault(email) { db -> snapshot(db, holdingId, input) }

    fun approve(email: String, previous: Review,
        explicitOwnerApproval: Boolean): Receipt {
        require(explicitOwnerApproval) { "Interest requires explicit approval" }
        return accounts.withUnlockedVault(email) { db ->
            val current = snapshot(db, previous.holdingId, previous.input)
            require(current.reviewSha256 == previous.reviewSha256 &&
                current.coupons == previous.coupons) {
                "Holdings or interest history changed after review"
            }
            val owner = db.rawQuery("SELECT h.entity_id,h.account_id,i.name " +
                "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(previous.holdingId)).use { c ->
                require(c.moveToFirst())
                Triple(c.getString(0),
                    if (c.isNull(1)) null else c.getString(1), c.getString(2))
            }
            val ids = mutableListOf<String>()
            for (coupon in current.coupons) {
                val source = "native-coupon:${previous.holdingId}:${coupon.date}"
                require(!db.has("SELECT 1 FROM ledger_event WHERE source_ref=?", source)) {
                    "Coupon has already been posted"
                }
                val id = UUID.randomUUID().toString()
                db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id," +
                    "holding_id,holding_name,account_id,event_type,trade_date," +
                    "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
                    "entered_at,entered_by,source_ref,notes) " +
                    "VALUES(?,?,?,?,?,'INTEREST',?,0,0,?,0,?,?,?,?)",
                    arrayOf<Any?>(id, owner.first, previous.holdingId,
                        owner.third, owner.second, coupon.date.toString(),
                        coupon.amountMinor, Instant.now().toString(),
                        "owner-reviewed-interest", source,
                        "${previous.input.kind} contractual coupon: owner-confirmed " +
                            "principal and rate, not an issuer-reconciled receipt"))
                ids += id
            }
            Receipt(ids.size, ids)
        }
    }
}
