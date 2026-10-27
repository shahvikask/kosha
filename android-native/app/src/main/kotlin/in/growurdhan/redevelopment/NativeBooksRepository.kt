package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeAssetTaxonomy
import `in`.growurdhan.model.NativePortfolioMath
import `in`.growurdhan.model.NativeEmiPlanner
import `in`.growurdhan.model.NativeMoneyWeightedReturn
import `in`.growurdhan.model.NativeTaxLotTrace
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Persistent native books. Every operation requires a fresh device-bound, password-verified
 * vault session. withUnlockedVault wraps edits in a SQLCipher transaction and rechecks
 * admission/credentials before committing. Never writes a plaintext financial cache.
 */
internal class NativeBooksRepository(private val accounts: NativeAccountRepository) {
    data class Person(val id: String, val name: String, val relation: String,
        val kind: String, val isPrimary: Boolean, val active: Boolean, val maskedPan: String?)
    data class Custody(val id: String, val personId: String, val type: String,
        val custodian: String, val maskedReference: String?, val nominee: String?)
    data class Goal(val id: String, val personId: String?, val name: String,
        val targetMinor: Long?, val targetDate: String?, val priority: String?)
    data class Holding(val id: String, val personId: String, val personName: String,
        val accountId: String?, val instrumentId: String, val name: String,
        val category: String, val subcategory: String, val quantityScaled: Long,
        val marketPriceMinor: Long, val priceDate: String?, val priceSource: String?,
        val knownCostMinor: Long, val unknownCost: Boolean, val needsReview: Boolean,
        val archived: Boolean, val notes: String?)
    data class EventRow(val id: String, val holdingId: String, val ownerId: String,
        val kind: String, val date: String, val quantityScaled: Long, val unitPriceMinor: Long,
        val amountMinor: Long, val chargesMinor: Long, val supersedes: String?, val notes: String?)
    data class ImportBatch(val id: String, val filename: String, val documentKind: String,
        val statementDate: String?, val newRows: Int, val duplicateRows: Int,
        val conflicts: Int, val committedAt: String)
    data class OriginalDocument(val id: String, val batchId: String, val filename: String,
        val kind: String, val sha256: String, val byteLength: Long, val importedAt: String)
    data class Snapshot(val people: List<Person>, val custody: List<Custody>,
        val goals: List<Goal>, val holdings: List<Holding>,
        val imports: List<ImportBatch>, val documents: List<OriginalDocument>)

    data class NewPerson(val name: String, val relation: String,
        val kind: String = "INDIVIDUAL", val dob: String? = null,
        val guardianId: String? = null, val panForStatements: String? = null)
    data class NewCustody(val personId: String, val type: String, val custodian: String,
        val identifierMasked: String? = null, val dpId: String? = null,
        val clientId: String? = null, val boId: String? = null, val nominee: String? = null)
    data class NewHolding(val personId: String, val accountId: String?,
        val name: String, val category: String, val subcategory: String,
        val quantityScaled: Long, val priceMinor: Long, val date: String,
        val knownOpeningCost: Boolean, val isin: String? = null,
        val notes: String? = null, val detailsJson: String? = null,
        val openingUnitPriceMinor: Long? = null, val institutionName: String? = null,
        val accountNumberMasked: String? = null, val maturityDate: String? = null,
        val couponRateBps: Int? = null, val interestRateBps: Int? = null,
        val monthlyEmiMinor: Long? = null, val emiStatus: String? = null,
        val remainingEmiCount: Int? = null, val location: String? = null,
        val folioForStatements: String? = null)
    data class NewEvent(val holdingId: String, val kind: String, val date: String,
        val quantityScaled: Long, val unitPriceMinor: Long, val amountMinor: Long,
        val chargesMinor: Long = 0L, val supersedes: String? = null,
        val notes: String? = null)

    private fun clean(text: String, max: Int = 128): String {
        val value = text.trim().replace(Regex("\\s+"), " ")
        require(value.isNotEmpty() && value.length <= max && value.none { it.isISOControl() }) {
            "Invalid book field"
        }
        return value
    }
    private fun date(text: String) = LocalDate.parse(text).toString()
    private fun optionalDate(text: String?): String? = text?.takeIf { it.isNotBlank() }?.let(::date)
    private fun now() = Instant.now().toString()
    private fun newId() = UUID.randomUUID().toString()
    private fun SQLiteDatabase.exists(query: String, vararg args: String): Boolean =
        rawQuery(query, args).use { it.moveToFirst() }
    private fun requirePerson(db: SQLiteDatabase, personId: String) {
        require(db.exists("SELECT 1 FROM entity WHERE entity_id=? AND is_active=1", personId)) {
            "Inactive or unknown owner"
        }
    }
    private fun requireCustody(db: SQLiteDatabase, owner: String, accountId: String?) {
        if (accountId != null) require(db.exists(
            "SELECT 1 FROM account WHERE account_id=? AND entity_id=? AND closed_on IS NULL",
            accountId, owner)) { "Custody account does not belong to owner" }
    }
    private fun requireHoldingOwner(db: SQLiteDatabase, holdingId: String): Pair<String, String?> =
        db.rawQuery("SELECT entity_id,account_id FROM holding WHERE holding_id=?",
            arrayOf(holdingId)).use { cursor ->
            require(cursor.moveToFirst()) { "Unknown holding" }
            val owner = cursor.getString(0)
            val custody = if (cursor.isNull(1)) null else cursor.getString(1)
            requirePerson(db, owner)
            requireCustody(db, owner, custody)
            owner to custody
        }

    fun read(email: String): Snapshot = accounts.withUnlockedVault(email) { db ->
        val people = mutableListOf<Person>()
        db.rawQuery("SELECT entity_id,display_name,relation,entity_type,is_primary,is_active,pan_masked " +
            "FROM entity ORDER BY is_primary DESC, display_name", null).use { c ->
            while (c.moveToNext()) people += Person(c.getString(0), c.getString(1),
                c.getString(2), c.getString(3), c.getInt(4) == 1, c.getInt(5) == 1,
                if (c.isNull(6)) null else c.getString(6))
        }
        val custody = mutableListOf<Custody>()
        db.rawQuery("SELECT account_id,entity_id,account_type,custodian,identifier_masked,nominee " +
            "FROM account ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) custody += Custody(c.getString(0), c.getString(1),
                c.getString(2), c.getString(3),
                if (c.isNull(4)) null else c.getString(4),
                if (c.isNull(5)) null else c.getString(5))
        }
        val goals = mutableListOf<Goal>()
        db.rawQuery("SELECT goal_id,entity_id,name,target_minor,target_date,priority FROM goal " +
            "ORDER BY target_date", null).use { c ->
            while (c.moveToNext()) goals += Goal(c.getString(0),
                if (c.isNull(1)) null else c.getString(1), c.getString(2),
                if (c.isNull(3)) null else c.getLong(3),
                if (c.isNull(4)) null else c.getString(4),
                if (c.isNull(5)) null else c.getString(5))
        }
        val holdings = mutableListOf<Holding>()
        db.rawQuery("SELECT h.holding_id,h.entity_id,e.display_name,h.account_id,h.instrument_id," +
            "i.name,i.category,i.sub_category,h.current_price_minor,h.last_price_updated," +
            "h.price_source,h.needs_review,h.notes " +
            "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
            "JOIN entity e ON e.entity_id=h.entity_id ORDER BY i.name", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val kind = c.getString(6)
                val fold = NativePortfolioMath.fold(readRawEvents(db, id), kind)
                // A holding with zero units remains visible, including its immutable audit history.
                holdings += Holding(id, c.getString(1), c.getString(2),
                    if (c.isNull(3)) null else c.getString(3), c.getString(4),
                    c.getString(5), kind, c.getString(7), fold.quantityScaled,
                    c.getLong(8), if (c.isNull(9)) null else c.getString(9),
                    if (c.isNull(10)) null else c.getString(10),
                    fold.knownCostMinor, fold.hasUnknownCost,
                    c.getInt(11) == 1 || fold.errors.isNotEmpty(),
                    db.rawQuery("SELECT value FROM setting WHERE key=?",
                        arrayOf("native.holding.archived.$id")).use { archived ->
                            archived.moveToFirst() && archived.getString(0) == "1"
                        }, if (c.isNull(12)) null else c.getString(12))
            }
        }
        val imports = mutableListOf<ImportBatch>()
        db.rawQuery("SELECT import_batch_id,source_filename,document_kind,statement_date," +
            "rows_new,rows_duplicate,rows_conflict,committed_at FROM import_batch " +
            "ORDER BY committed_at DESC", null).use { c ->
            while (c.moveToNext()) imports += ImportBatch(c.getString(0), c.getString(1),
                c.getString(2), if (c.isNull(3)) null else c.getString(3),
                c.getInt(4), c.getInt(5), c.getInt(6), c.getString(7))
        }
        val documents = mutableListOf<OriginalDocument>()
        db.rawQuery("SELECT source_document_id,import_batch_id,original_filename," +
            "document_kind,sha256,byte_length,imported_at FROM source_document " +
            "ORDER BY imported_at DESC", null).use { c ->
            while (c.moveToNext()) documents += OriginalDocument(c.getString(0),
                c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getLong(5), c.getString(6))
        }
        Snapshot(people, custody, goals, holdings, imports, documents)
    }

    fun createPerson(email: String, form: NewPerson): String =
        accounts.withUnlockedVault(email) { db ->
            val name = clean(form.name)
            require(form.relation in setOf("Spouse", "Parent", "Child", "HUF", "Joint")) {
                "Primary Self cannot be duplicated"
            }
            require(form.kind in setOf("INDIVIDUAL", "HUF", "PROPRIETORSHIP",
                "PRIVATE_TRUST", "PARTNERSHIP_FIRM"))
            if (form.kind == "HUF") require(form.relation == "HUF")
            val birth = optionalDate(form.dob)
            form.guardianId?.let { guardian ->
                requirePerson(db, guardian)
                require(db.exists("SELECT 1 FROM entity WHERE entity_id=? " +
                    "AND entity_type='INDIVIDUAL' AND relation!='Child' " +
                    "AND is_active=1", guardian)) {
                    "Guardian must be an active adult-eligible person"
                }
            }
            if (form.relation == "Child" && birth != null &&
                LocalDate.parse(birth).isAfter(LocalDate.now().minusYears(18))) {
                require(form.guardianId != null) { "Minor requires a guardian" }
            }
            val pan = form.panForStatements?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            if (pan != null) require(Regex("[A-Z]{5}[0-9]{4}[A-Z]").matches(pan)) {
                "Invalid statement PAN"
            }
            val id = newId()
            db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,pan_masked," +
                "pan_for_statements,date_of_birth,guardian_id,relation,is_primary,is_active,created_at) " +
                "VALUES(?,?,?,?,?,?,?,?,0,1,?)", arrayOf<Any?>(
                id, form.kind, name, pan?.let { it.take(5) + "****" + it.last() },
                pan, birth, form.guardianId, form.relation, now()))
            id
        }

    fun archivePerson(email: String, personId: String) =
        accounts.withUnlockedVault(email) { db ->
            require(db.exists("SELECT 1 FROM entity WHERE entity_id=? AND is_primary=0 AND is_active=1",
                personId)) { "Primary or unknown member cannot be removed" }
            // Never silently orphan financial records, nominated custody or a minor's guardian.
            for (table in listOf("holding", "account", "goal", "ledger_event", "liability")) {
                require(!db.exists("SELECT 1 FROM $table WHERE entity_id=?", personId)) {
                    "Member has linked financial records"
                }
            }
            require(!db.exists("SELECT 1 FROM entity WHERE guardian_id=? AND is_active=1", personId)) {
                "Member is an active guardian"
            }
            db.execSQL("UPDATE entity SET is_active=0 WHERE entity_id=?", arrayOf<Any>(personId))
        }

    fun createCustody(email: String, form: NewCustody): String =
        accounts.withUnlockedVault(email) { db ->
            requirePerson(db, form.personId)
            val custodian = clean(form.custodian)
            val type = clean(form.type, 40)
            require(type in setOf("DEMAT", "FOLIO", "BANK", "OTHER", "NPS", "BROKER"))
            // Only masked visible identifiers; full DP/BO references remain encrypted in SQLCipher.
            val identifier = form.identifierMasked?.takeIf { it.isNotBlank() }?.also {
                require(it.length <= 80 && '*' in it) { "Visible account ID must be masked" }
            }
            // Account identities participate in exact custody reconciliation. Never truncate
            // identifiers: two different accounts can otherwise become the same key.
            for (value in listOf(form.dpId, form.clientId, form.boId)) {
                if (value != null) require(value.isNotBlank() && value.length <= 100 &&
                    value.none { it.isISOControl() }) { "Invalid custody identifier" }
            }
            form.nominee?.let { require(it.length <= 128 &&
                it.none { char -> char.isISOControl() }) { "Invalid nominee field" } }
            val id = newId()
            db.execSQL("INSERT INTO account(account_id,entity_id,account_type,custodian," +
                "identifier_masked,dp_id,client_id,bo_id,nominee,created_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                id, form.personId, type, custodian, identifier, form.dpId, form.clientId, form.boId,
                form.nominee, now()))
            id
        }

    fun createGoal(email: String, personId: String?, name: String, targetMinor: Long,
        targetDate: String, priority: String): String =
        accounts.withUnlockedVault(email) { db ->
            if (personId != null) requirePerson(db, personId)
            require(targetMinor > 0L && priority in setOf("LOW", "MEDIUM", "HIGH")) {
                "Invalid goal target"
            }
            val id = newId()
            db.execSQL("INSERT INTO goal(goal_id,entity_id,name,target_minor,target_date,priority," +
                "created_at) VALUES(?,?,?,?,?,?,?)", arrayOf<Any?>(
                id, personId, clean(name), targetMinor, date(targetDate), priority, now()))
            id
        }

    fun createHolding(email: String, form: NewHolding): String =
        accounts.withUnlockedVault(email) { db ->
            requirePerson(db, form.personId)
            requireCustody(db, form.personId, form.accountId)
            NativeAssetTaxonomy.requireValid(form.category, form.subcategory)
            require(form.quantityScaled >= 0L && form.priceMinor >= 0L) {
                "Negative opening quantity or price"
            }
            val acquired = date(form.date)
            val acquisitionUnit = form.openingUnitPriceMinor ?: form.priceMinor
            require(acquisitionUnit >= 0L) { "Opening cost cannot be negative" }
            val maturity = optionalDate(form.maturityDate)
            if (maturity != null) require(maturity >= acquired) { "Maturity predates acquisition" }
            form.couponRateBps?.let { require(it in 0..10000) }
            form.interestRateBps?.let { require(it in 0..10000) }
            form.monthlyEmiMinor?.let { require(it >= 0L) }
            form.emiStatus?.let { require(it in setOf("PAYING", "PAUSED", "STOPPED")) }
            form.remainingEmiCount?.let { require(it >= 0) }
            form.accountNumberMasked?.let { require('*' in it && it.length <= 100) }
            form.isin?.let { require(it.matches(Regex("[A-Z0-9]{12}"))) }
            form.folioForStatements?.let { folio ->
                require(form.category == "MUTUAL_FUND" &&
                    folio.isNotBlank() && folio.length <= 120 &&
                    folio.none { it.isISOControl() }) {
                    "Statement folio requires a valid mutual fund holding"
                }
                require(form.accountId == null || db.exists(
                    "SELECT 1 FROM account WHERE account_id=? AND entity_id=? " +
                        "AND account_type='FOLIO' AND closed_on IS NULL",
                    form.accountId, form.personId)) {
                    "Mutual fund folio cannot be assigned to a demat account"
                }
            }
            form.location?.let { require(it.length <= 1024) }
            val name = clean(form.name, 240)
            form.detailsJson?.let {
                require(it.length <= 32768)
                org.json.JSONObject(it) // Do not silently replace malformed specialist metadata.
            }
            val instrument = newId()
            val holding = newId()
            val timestamp = now()
            db.execSQL("INSERT INTO instrument(instrument_id,name,category,sub_category,isin," +
                "symbol_or_folio,is_user_created,created_at) VALUES(?,?,?,?,?,?,1,?)",
                arrayOf<Any?>(instrument, name, form.category, form.subcategory,
                    form.isin, form.folioForStatements, timestamp))
            db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id,account_id," +
                "current_price_minor,last_price_updated,price_source,acquired_on,notes,details_json," +
                "institution_name,account_number_masked,maturity_date,coupon_rate_bps," +
                "interest_rate_bps,monthly_emi_minor,emi_status,remaining_emi_count," +
                "location,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    holding, instrument, form.personId, form.accountId, form.priceMinor,
                    acquired, "USER_ENTRY", acquired, form.notes?.take(2048),
                    form.detailsJson, form.institutionName?.let { clean(it) },
                    form.accountNumberMasked, maturity, form.couponRateBps,
                    form.interestRateBps, form.monthlyEmiMinor, form.emiStatus,
                    form.remainingEmiCount, form.location, timestamp))
            if (form.quantityScaled > 0L) {
                val openingId = insertEvent(db, form.personId, form.accountId, holding, name,
                    NewEvent(holding, "OPENING_BALANCE", acquired, form.quantityScaled,
                        if (form.knownOpeningCost) acquisitionUnit else 0L,
                        0L, notes = if (form.knownOpeningCost) null else "Opening cost unknown"))
                db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                    arrayOf<Any>("native.ledger.cost_source.$openingId",
                        if (form.knownOpeningCost) "KNOWN" else "UNKNOWN"))
            }
            holding
        }

    fun appendUserEvent(email: String, form: NewEvent): String =
        accounts.withUnlockedVault(email) { db ->
            require(form.kind != "SNAPSHOT_ADJUSTMENT") {
                "Statement snapshots require a verified import-review transaction"
            }
            val (owner, custody) = requireHoldingOwner(db, form.holdingId)
            require(!db.exists("SELECT 1 FROM setting WHERE key=? AND value='1'",
                "native.holding.archived.${form.holdingId}")) {
                "Reopen a closed holding before entering new money events"
            }
            val name = db.rawQuery("SELECT i.name FROM instrument i " +
                "JOIN holding h ON i.instrument_id=h.instrument_id WHERE h.holding_id=?",
                arrayOf(form.holdingId)).use { c ->
                check(c.moveToFirst())
                c.getString(0)
            }
            val id = insertEvent(db, owner, custody, form.holdingId, name, form)
            // Reject transactions that would make quantity negative or change a foreign lot.
            val category = db.rawQuery("SELECT i.category FROM instrument i " +
                "JOIN holding h ON i.instrument_id=h.instrument_id WHERE h.holding_id=?",
                arrayOf(form.holdingId)).use { c -> check(c.moveToFirst()); c.getString(0) }
            val fold = NativePortfolioMath.fold(readRawEvents(db, form.holdingId), category)
            require(fold.errors.isEmpty()) { "Event breaks quantity/history invariants: ${fold.errors}" }
            id
        }

    private fun insertEvent(db: SQLiteDatabase, owner: String, custody: String?,
        holding: String, holdingName: String, form: NewEvent,
        sourceRef: String? = null): String {
        require(form.kind in NativePortfolioMath.eventTypes)
        require(form.chargesMinor >= 0L && form.unitPriceMinor >= 0L) { "Negative charges/price" }
        val settled = date(form.date)
        if (form.kind == "CORRECTION") {
            require(form.supersedes != null && db.exists(
                "SELECT 1 FROM ledger_event WHERE ledger_event_id=? AND holding_id=? AND entity_id=?",
                form.supersedes, holding, owner)) { "Unverified correction target" }
        } else require(form.supersedes == null)
        when (form.kind) {
            "BUY", "OPENING_BALANCE", "BONUS" -> require(form.quantityScaled > 0L)
            "SELL" -> require(form.quantityScaled != 0L && form.quantityScaled != Long.MIN_VALUE)
            "SPLIT" -> require(form.quantityScaled > 0L && form.unitPriceMinor > 0L)
            "CORRECTION" -> require(form.supersedes != null)
            else -> Unit
        }
        val id = newId()
        db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id,holding_name," +
            "account_id,event_type,trade_date,quantity_scaled,unit_price_minor," +
            "total_amount_minor,charges_minor,supersedes_event_id,notes,entered_at,entered_by,source_ref)" +
            " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
            id, owner, holding, holdingName, custody, form.kind, settled,
            form.quantityScaled, form.unitPriceMinor, form.amountMinor,
            form.chargesMinor, form.supersedes, form.notes?.take(2048), now(), "owner", sourceRef))
        return id
    }

    private fun readRawEvents(db: SQLiteDatabase, holdingId: String): List<NativePortfolioMath.Event> {
        val rows = mutableListOf<NativePortfolioMath.Event>()
        db.rawQuery("SELECT ledger_event_id,entity_id,holding_id,event_type,trade_date," +
            "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
            "supersedes_event_id,notes FROM ledger_event WHERE holding_id=?",
            arrayOf(holdingId)).use { c ->
            while (c.moveToNext()) {
                val kind = c.getString(3)
                val price = c.getLong(6)
                val note = if (c.isNull(10)) "" else c.getString(10)
                val provenance = db.rawQuery("SELECT value FROM setting WHERE key=?",
                    arrayOf("native.ledger.cost_source.${c.getString(0)}")).use { p ->
                    if (p.moveToFirst()) p.getString(0) else null
                }
                rows += NativePortfolioMath.Event(c.getString(0), c.getString(1),
                    c.getString(2), kind, c.getString(4), c.getLong(5), price,
                    c.getLong(7), c.getLong(8),
                    if (c.isNull(9)) null else c.getString(9),
                    // Explicit SQLCipher provenance distinguishes true zero cost from unknown.
                    // For historical files without provenance, an absent price/amount
                    // or documented unknown-cost note is conservatively unknown.
                    when (provenance) {
                        "KNOWN" -> true
                        "UNKNOWN" -> false
                        else -> !(kind in setOf("OPENING_BALANCE", "SNAPSHOT_ADJUSTMENT") &&
                            ((price == 0L && c.getLong(7) == 0L) ||
                                note.contains("unknown", ignoreCase = true)))
                    })
            }
        }
        return rows
    }

    /**
     * Read-only XIRR from authenticated events and last recorded unit price.
     * Unknown invested, invalid FIFO, unreconciled holdings, or no actual
     * investor cash flows yield N/A. Nothing is calculated or cached offline
     * without an admitted SQLCipher session. No price is fetched here.
     */
    /**
     * Historic XIRR cannot use a later NAV or future transaction. Select the
     * latest price observation on/before asOf with same-day USER_OVERRIDE
     * precedence; a current holding price is a fallback only when its own
     * original date is no later than asOf. No online price is invented.
     */
    fun moneyWeightedReturn(email: String, holdingId: String,
        asOf: String = LocalDate.now().toString()): Double? =
        accounts.withUnlockedVault(email) { db ->
            requireHoldingOwner(db, holdingId)
            val day = LocalDate.parse(asOf)
            require(day.toString() == asOf && !day.isAfter(LocalDate.now())) {
                "Cannot calculate a future or noncanonical historical return"
            }
            val info = db.rawQuery("SELECT i.category,i.instrument_id," +
                "h.current_price_minor,h.needs_review,h.last_price_updated " +
                "FROM holding h JOIN instrument i ON i.instrument_id=h.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(holdingId)).use { cur ->
                require(cur.moveToFirst())
                listOf(
                    cur.getString(0), cur.getString(1), cur.getLong(2).toString(),
                    cur.getInt(3).toString(),
                    if (cur.isNull(4)) "" else cur.getString(4))
            }
            if (info[3] == "1") return@withUnlockedVault null
            // price_observation is keyed by instrument, not holding. Shared
            // instruments can have differing manual member-specific prices.
            // Do not borrow another holding's NAV for this owner's XIRR.
            val ambiguousPriceScope = db.rawQuery(
                "SELECT COUNT(*) FROM holding WHERE instrument_id=?",
                arrayOf(info[1])).use { cur ->
                require(cur.moveToFirst())
                cur.getLong(0) != 1L
            }
            if (ambiguousPriceScope) return@withUnlockedVault null
            val observed = db.rawQuery(
                "SELECT price_minor FROM price_observation " +
                    "WHERE instrument_id=? AND price_date<=? " +
                    "ORDER BY price_date DESC, " +
                    "CASE WHEN source='USER_OVERRIDE' THEN 1 ELSE 0 END DESC," +
                    "ingested_at DESC LIMIT 1",
                arrayOf(info[1], asOf)).use { cur ->
                if (cur.moveToFirst()) cur.getLong(0) else null
            }
            val currentDate = info[4].takeIf { it.isNotEmpty() }?.let {
                if (it.length == 10) LocalDate.parse(it)
                else Instant.parse(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            }
            val fallback = if (currentDate != null && !currentDate.isAfter(day))
                info[2].toLong() else null
            val priced = observed ?: fallback
            if (priced == null || priced < 0L) null
            else NativeMoneyWeightedReturn.forHolding(
                readRawEvents(db, holdingId).filter {
                    !LocalDate.parse(it.date).isAfter(day)
                }, info[0], priced, day)
        }

    /** Review-only parcel facts; never a statutory tax estimate. */
    fun taxWorkpaper(email: String, holdingId: String,
        assessmentYear: String): NativeTaxLotTrace.Workpaper =
        accounts.withUnlockedVault(email) { db ->
            requireHoldingOwner(db, holdingId)
            val category = db.rawQuery("SELECT i.category FROM holding h " +
                "JOIN instrument i ON h.instrument_id=i.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(holdingId)).use { cur ->
                require(cur.moveToFirst())
                cur.getString(0)
            }
            NativeTaxLotTrace.trace(readRawEvents(db, holdingId), category,
                assessmentYear)
        }

    fun history(email: String, holdingId: String): List<EventRow> =
        accounts.withUnlockedVault(email) { db ->
            requireHoldingOwner(db, holdingId)
            val rows = mutableListOf<EventRow>()
            db.rawQuery("SELECT ledger_event_id,entity_id,event_type,trade_date," +
                "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
                "supersedes_event_id,notes FROM ledger_event WHERE holding_id=? " +
                "ORDER BY trade_date,ledger_event_id", arrayOf(holdingId)).use { c ->
                while (c.moveToNext()) rows += EventRow(c.getString(0), holdingId,
                    c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                    c.getLong(5), c.getLong(6), c.getLong(7),
                    if (c.isNull(8)) null else c.getString(8),
                    if (c.isNull(9)) null else c.getString(9))
            }
            rows
        }

    /**
     * Edit profile facts in-place; immutable historical ledger ownership is never reassigned.
     * A primary Self can change profile details but cannot become a different legal entity.
     */
    fun updatePerson(email: String, personId: String, form: NewPerson) =
        accounts.withUnlockedVault(email) { db ->
            requirePerson(db, personId)
            val original = db.rawQuery("SELECT entity_type,relation,is_primary FROM entity WHERE entity_id=?",
                arrayOf(personId)).use { c ->
                check(c.moveToFirst())
                Triple(c.getString(0), c.getString(1), c.getInt(2) == 1)
            }
            val isPrimary = original.third
            if (form.kind != original.first || form.relation != original.second) {
                for (table in listOf("holding", "ledger_event", "account", "goal", "liability")) {
                    require(!db.exists("SELECT 1 FROM $table WHERE entity_id=?", personId)) {
                        "Legal ownership/classification cannot change with linked records"
                    }
                }
            }
            if (isPrimary) require(form.relation == "Self" && form.kind == "INDIVIDUAL") {
                "Primary identity cannot be reclassified"
            } else require(form.relation in setOf("Spouse", "Parent", "Child", "HUF", "Joint"))
            require(form.kind in setOf("INDIVIDUAL", "HUF", "PROPRIETORSHIP",
                "PRIVATE_TRUST", "PARTNERSHIP_FIRM"))
            if (form.kind == "HUF") require(form.relation == "HUF")
            val birth = optionalDate(form.dob)
            if (form.guardianId != null) {
                require(form.guardianId != personId) { "A member cannot be their own guardian" }
                requirePerson(db, form.guardianId)
                require(db.exists("SELECT 1 FROM entity WHERE entity_id=? " +
                    "AND entity_type='INDIVIDUAL' AND relation!='Child' " +
                    "AND is_active=1", form.guardianId)) {
                    "Guardian must be an active adult-eligible person"
                }
            }
            if (form.relation == "Child" && birth != null &&
                LocalDate.parse(birth).isAfter(LocalDate.now().minusYears(18))) {
                require(form.guardianId != null) { "A minor must have an active guardian" }
            }
            val pan = form.panForStatements?.trim()?.uppercase(java.util.Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }
            if (pan != null) require(Regex("[A-Z]{5}[0-9]{4}[A-Z]").matches(pan))
            val masked = pan?.let { it.take(5) + "****" + it.last() }
            // Null identity input intentionally means retain stored PAN/DOB, not erase it.
            db.execSQL("UPDATE entity SET display_name=?,relation=?,entity_type=?," +
                "guardian_id=COALESCE(?,guardian_id),date_of_birth=COALESCE(?,date_of_birth)," +
                "pan_masked=COALESCE(?,pan_masked)," +
                "pan_for_statements=COALESCE(?,pan_for_statements) WHERE entity_id=?",
                arrayOf<Any?>(clean(form.name), form.relation, form.kind,
                    form.guardianId, birth, masked, pan, personId))
        }

    /** Custody details may be changed, never the legal owner of an existing account. */
    fun updateCustody(email: String, accountId: String, form: NewCustody) =
        accounts.withUnlockedVault(email) { db ->
            requireCustody(db, form.personId, accountId)
            require(form.type in setOf("DEMAT", "FOLIO", "BANK", "OTHER", "NPS", "BROKER"))
            val originalType = db.rawQuery("SELECT account_type FROM account WHERE account_id=?",
                arrayOf(accountId)).use { c -> check(c.moveToFirst()); c.getString(0) }
            if (form.type != originalType) require(!db.exists(
                "SELECT 1 FROM holding WHERE account_id=?", accountId)) {
                "Cannot reclassify a custody account linked to holdings"
            }
            val visible = form.identifierMasked?.takeIf { it.isNotBlank() }?.also {
                require(it.length <= 80 && '*' in it) { "Visible account ID must be masked" }
            }
            // Editing must obey the same no-truncation identity rule as account creation.
            for (value in listOf(form.dpId, form.clientId, form.boId)) {
                if (value != null) require(value.isNotBlank() && value.length <= 100 &&
                    value.none { it.isISOControl() }) { "Invalid custody identifier" }
            }
            form.nominee?.let { require(it.length <= 128 &&
                it.none { char -> char.isISOControl() }) { "Invalid nominee field" } }
            db.execSQL("UPDATE account SET custodian=?,account_type=?," +
                "identifier_masked=COALESCE(?,identifier_masked)," +
                "dp_id=COALESCE(?,dp_id),client_id=COALESCE(?,client_id)," +
                "bo_id=COALESCE(?,bo_id),nominee=COALESCE(?,nominee) WHERE account_id=?",
                arrayOf<Any?>(clean(form.custodian), form.type, visible,
                    form.dpId, form.clientId, form.boId,
                    form.nominee, accountId))
        }

    /** Closing is a dated operation, not deletion of history or a financial transaction. */
    fun closeCustody(email: String, personId: String, accountId: String, closedOn: String) =
        accounts.withUnlockedVault(email) { db ->
            requireCustody(db, personId, accountId)
            val day = date(closedOn)
            val attached = mutableListOf<Pair<String, String>>()
            db.rawQuery("SELECT h.holding_id,i.category FROM holding h JOIN instrument i " +
                "ON h.instrument_id=i.instrument_id WHERE h.account_id=? AND h.entity_id=?",
                arrayOf(accountId, personId)).use { c ->
                while (c.moveToNext()) attached += c.getString(0) to c.getString(1)
            }
            for ((holding, kind) in attached) {
                val position = NativePortfolioMath.fold(readRawEvents(db, holding), kind)
                require(position.errors.isEmpty() && position.quantityScaled == 0L) {
                    "Close or transfer existing holdings before closing custody"
                }
            }
            db.execSQL("UPDATE account SET closed_on=? WHERE account_id=?",
                arrayOf<Any>(day, accountId))
        }

    fun updateGoal(email: String, goalId: String, personId: String?,
        name: String, targetMinor: Long, targetDate: String, priority: String) =
        accounts.withUnlockedVault(email) { db ->
            if (personId != null) requirePerson(db, personId)
            require(targetMinor > 0L && priority in setOf("LOW", "MEDIUM", "HIGH"))
            val originalOwner = db.rawQuery("SELECT entity_id FROM goal WHERE goal_id=?",
                arrayOf(goalId)).use { c ->
                require(c.moveToFirst()) { "Unknown goal" }
                if (c.isNull(0)) null else c.getString(0)
            }
            require(originalOwner == personId) { "Goal reassignment requires separate review" }
            db.execSQL("UPDATE goal SET name=?,target_minor=?,target_date=?,priority=? " +
                "WHERE goal_id=?", arrayOf<Any>(clean(name), targetMinor,
                    date(targetDate), priority, goalId))
        }

    fun removeUnlinkedGoal(email: String, goalId: String) =
        accounts.withUnlockedVault(email) { db ->
            require(db.exists("SELECT 1 FROM goal WHERE goal_id=?", goalId)) { "Unknown goal" }
            require(!db.exists("SELECT 1 FROM holding WHERE tag=?", goalId)) {
                "Cannot remove a goal linked to a holding"
            }
            db.execSQL("DELETE FROM goal WHERE goal_id=?", arrayOf<Any>(goalId))
        }

    /**
     * Metadata/account changes are not an edit to historical purchases, dates or costs.
     * All original specialist fields remain in details_json unless explicitly replaced.
     */
    fun updateHoldingMetadata(email: String, holdingId: String, accountId: String?,
        notes: String?, detailsJson: String?, location: String?) =
        accounts.withUnlockedVault(email) { db ->
            val (owner, originalAccount) = requireHoldingOwner(db, holdingId)
            requireCustody(db, owner, accountId)
            // A custody switch must never relabel immutable trades or reviewed CAS/CSV
            // positions to another account. Moving invested assets needs a separate,
            // provenance-preserving transfer flow, not a metadata edit.
            if (accountId != originalAccount) {
                require(!db.exists("SELECT 1 FROM ledger_event WHERE holding_id=?", holdingId) &&
                    !db.exists("SELECT 1 FROM statement_position WHERE holding_id=?", holdingId)) {
                    "Custody cannot change after a ledger event or reviewed statement position"
                }
            }
            require(notes == null || notes.length <= 2048)
            require(location == null || location.length <= 1024)
            if (detailsJson != null) {
                require(detailsJson.length <= 32768)
                org.json.JSONObject(detailsJson)
            }
            db.execSQL("UPDATE holding SET account_id=?,notes=COALESCE(?,notes)," +
                "details_json=COALESCE(?,details_json),location=COALESCE(?,location) " +
                "WHERE holding_id=?", arrayOf<Any?>(
                    accountId, notes, detailsJson, location, holdingId))
        }

    /** Only an owner-consistent goal can tag a holding; its original ledger is untouched. */
    fun assignGoal(email: String, holdingId: String, goalId: String?) =
        accounts.withUnlockedVault(email) { db ->
            val (owner, _) = requireHoldingOwner(db, holdingId)
            if (goalId != null) require(db.exists(
                "SELECT 1 FROM goal WHERE goal_id=? AND (entity_id=? OR entity_id IS NULL)",
                goalId, owner)) { "Goal does not belong to this member/household" }
            db.execSQL("UPDATE holding SET tag=? WHERE holding_id=?",
                arrayOf<Any?>(goalId, holdingId))
        }

    /** Archive is stored in the encrypted native setting ledger; no schema-v9 mutation. */
    fun setHoldingArchived(email: String, holdingId: String, archived: Boolean) =
        accounts.withUnlockedVault(email) { db ->
            requireHoldingOwner(db, holdingId)
            if (archived) {
                val category = db.rawQuery("SELECT i.category FROM instrument i " +
                    "JOIN holding h ON h.instrument_id=i.instrument_id WHERE h.holding_id=?",
                    arrayOf(holdingId)).use { c -> check(c.moveToFirst()); c.getString(0) }
                val position = NativePortfolioMath.fold(readRawEvents(db, holdingId), category)
                require(position.errors.isEmpty() && position.quantityScaled == 0L) {
                    "Cannot archive an open or unreconciled holding"
                }
            }
            db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>("native.holding.archived.$holdingId", if (archived) "1" else "0"))
        }

    /**
     * Idempotent offline loan catch-up matching frozen EMI policy. Authenticated
     * repayment events and the next-due cursor commit in the SAME SQLCipher transaction.
     * If any due day already has a ledger repayment, reconcile manually; never double post.
     */
    fun catchUpEmi(email: String, holdingId: String, asOf: String): NativeEmiPlanner.Plan? =
        accounts.withUnlockedVault(email) { db ->
            val (owner, custody) = requireHoldingOwner(db, holdingId)
            val loan = db.rawQuery("SELECT i.category,i.name,h.emi_status,h.monthly_emi_minor," +
                "h.next_coupon_date,h.remaining_emi_count,h.current_price_minor " +
                "FROM holding h JOIN instrument i ON h.instrument_id=i.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(holdingId)).use { c ->
                check(c.moveToFirst())
                val status = if (c.isNull(2)) null else c.getString(2)
                val emi = if (c.isNull(3)) 0L else c.getLong(3)
                val next = if (c.isNull(4)) null else c.getString(4)
                Pair(c.getString(1), NativeEmiPlanner.Loan(owner, holdingId,
                    c.getString(0), status ?: if (emi > 0L && next != null) "PAYING" else null,
                    emi, next, if (c.isNull(5)) 0 else c.getInt(5), c.getLong(6)))
            }
            require(!db.exists("SELECT 1 FROM setting WHERE key=? AND value='1'",
                "native.holding.archived.$holdingId")) { "Archived holding cannot accrue repayments" }
            val posted = mutableSetOf<String>()
            db.rawQuery("SELECT trade_date FROM ledger_event WHERE holding_id=? " +
                "AND entity_id=? AND event_type='REPAYMENT'",
                arrayOf(holdingId, owner)).use { c ->
                while (c.moveToNext()) posted.add(c.getString(0))
            }
            val asOfDate = date(asOf)
            require(!LocalDate.parse(asOfDate).isAfter(LocalDate.now())) {
                "Cannot post installments due in the future"
            }
            val plan = NativeEmiPlanner.plan(loan.second, asOfDate, posted)
                ?: return@withUnlockedVault null
            for (payment in plan.payments) {
                insertEvent(db, owner, custody, holdingId, loan.first,
                    NewEvent(holdingId, "REPAYMENT", payment.dueDate, 0L,
                        payment.amountMinor, payment.amountMinor,
                        notes = "Monthly EMI (automatic)"),
                    sourceRef = "native.emi.v1")
            }
            db.execSQL("UPDATE holding SET current_price_minor=?,next_coupon_date=?," +
                "remaining_emi_count=?,emi_status=?,last_price_updated=?,price_source=? " +
                "WHERE holding_id=?", arrayOf<Any?>(plan.outstandingMinor,
                    plan.nextDueDate, plan.remainingCount, plan.status, now(),
                    "EMI catch-up", holdingId))
            plan
        }

    /** Explicit repayment state; no events are added merely by pausing/resuming. */
    fun updateEmiSchedule(email: String, holdingId: String, status: String,
        monthlyMinor: Long, nextDue: String?, remaining: Int) =
        accounts.withUnlockedVault(email) { db ->
            requireHoldingOwner(db, holdingId)
            require(status in setOf("PAYING", "PAUSED", "STOPPED"))
            require(monthlyMinor >= 0L && remaining in 0..3600)
            if (status == "PAYING") {
                require(monthlyMinor > 0L && remaining > 0 && nextDue != null)
            }
            val category = db.rawQuery("SELECT i.category FROM instrument i " +
                "JOIN holding h ON i.instrument_id=h.instrument_id " +
                "WHERE h.holding_id=?", arrayOf(holdingId)).use { c ->
                check(c.moveToFirst()); c.getString(0)
            }
            require(category == "LIABILITY")
            db.execSQL("UPDATE holding SET emi_status=?,monthly_emi_minor=?," +
                "next_coupon_date=?,remaining_emi_count=? WHERE holding_id=?",
                arrayOf<Any?>(status, monthlyMinor, optionalDate(nextDue), remaining, holdingId))
        }

    /** Manual dated close/NAV only, preserving source and rejecting future data. */
    fun recordManualPrice(email: String, holdingId: String, priceMinor: Long,
        priceDate: String) = accounts.withUnlockedVault(email) { db ->
        require(priceMinor >= 0L)
        val day = LocalDate.parse(priceDate)
        require(!day.isAfter(LocalDate.now())) { "Future price is not allowed" }
        requireHoldingOwner(db, holdingId)
        val instrument = db.rawQuery("SELECT instrument_id FROM holding WHERE holding_id=?",
            arrayOf(holdingId)).use { c -> check(c.moveToFirst()); c.getString(0) }
        NativeDatedPriceRecorder.record(db, holdingId, instrument,
            day.toString(), priceMinor, "USER_OVERRIDE", now())
    }

    /** Official AMFI NAV only; no automatic NSE/BSE prices or guessed ISINs. */
    fun refreshAmfiNav(email: String): NativeAmfiNavRepository.Receipt =
        NativeAmfiNavRepository(accounts).refresh(email)

    /** Consent bookkeeping only. Google sign-in and Gmail API remain unconnected. */
    fun setMemberGmailOptIn(email: String, personId: String,
        approved: Boolean) =
        NativeGmailSyncLedger(accounts).setMemberOptIn(email, personId, approved)

    fun gmailConsentStates(email: String): Map<String, Boolean> =
        accounts.withUnlockedVault(email) { db ->
            val result = linkedMapOf<String, Boolean>()
            db.rawQuery("SELECT entity_id,gmail_sync FROM entity " +
                "WHERE is_active=1", null).use { cur ->
                while (cur.moveToNext()) {
                    result[cur.getString(0)] = cur.getInt(1) == 1
                }
            }
            result
        }

    companion object {
        /** Decimal input is converted once to exact scaled integers; never via Double. */
        fun parseMinor(display: String, scale: Int): Long =
            BigDecimal(display.trim()).movePointRight(scale).toBigIntegerExact().longValueExact()
        fun formatMinor(value: Long, scale: Int): String =
            BigDecimal(BigInteger.valueOf(value), scale).stripTrailingZeros().toPlainString()
    }
}
