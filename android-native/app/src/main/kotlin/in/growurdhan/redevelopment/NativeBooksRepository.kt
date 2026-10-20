package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeAssetTaxonomy
import `in`.growurdhan.model.NativePortfolioMath
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
    data class Snapshot(val people: List<Person>, val custody: List<Custody>,
        val goals: List<Goal>, val holdings: List<Holding>)

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
        val notes: String? = null, val detailsJson: String? = null)
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
                    false, if (c.isNull(12)) null else c.getString(12))
            }
        }
        Snapshot(people, custody, goals, holdings)
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
            form.guardianId?.let { requirePerson(db, it) }
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
            val id = newId()
            db.execSQL("INSERT INTO account(account_id,entity_id,account_type,custodian," +
                "identifier_masked,dp_id,client_id,bo_id,nominee,created_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                id, form.personId, type, custodian, identifier, form.dpId?.take(100),
                form.clientId?.take(100), form.boId?.take(100),
                form.nominee?.take(128), now()))
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
            val name = clean(form.name, 240)
            form.detailsJson?.let {
                require(it.length <= 32768)
                org.json.JSONObject(it) // Do not silently replace malformed specialist metadata.
            }
            val instrument = newId()
            val holding = newId()
            val timestamp = now()
            db.execSQL("INSERT INTO instrument(instrument_id,name,category,sub_category,isin," +
                "is_user_created,created_at) VALUES(?,?,?,?,?,1,?)",
                arrayOf<Any?>(instrument, name, form.category, form.subcategory,
                    form.isin?.takeIf { it.matches(Regex("[A-Z0-9]{12}")) }, timestamp))
            db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id,account_id," +
                "current_price_minor,last_price_updated,price_source,acquired_on,notes,details_json," +
                "created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                holding, instrument, form.personId, form.accountId, form.priceMinor,
                acquired, "USER_ENTRY", acquired, form.notes?.take(2048),
                form.detailsJson, timestamp))
            if (form.quantityScaled > 0L) {
                insertEvent(db, form.personId, form.accountId, holding, name,
                    NewEvent(holding, "OPENING_BALANCE", acquired, form.quantityScaled,
                        if (form.knownOpeningCost) form.priceMinor else 0L,
                        0L, notes = if (form.knownOpeningCost) null else "Opening cost unknown"))
            }
            holding
        }

    fun appendUserEvent(email: String, form: NewEvent): String =
        accounts.withUnlockedVault(email) { db ->
            require(form.kind != "SNAPSHOT_ADJUSTMENT") {
                "Statement snapshots require a verified import-review transaction"
            }
            val (owner, custody) = requireHoldingOwner(db, form.holdingId)
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
        holding: String, holdingName: String, form: NewEvent): String {
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
            "total_amount_minor,charges_minor,supersedes_event_id,notes,entered_at,entered_by)" +
            " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
            id, owner, holding, holdingName, custody, form.kind, settled,
            form.quantityScaled, form.unitPriceMinor, form.amountMinor,
            form.chargesMinor, form.supersedes, form.notes?.take(2048), now(), "owner"))
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
                rows += NativePortfolioMath.Event(c.getString(0), c.getString(1),
                    c.getString(2), kind, c.getString(4), c.getLong(5), price,
                    c.getLong(7), c.getLong(8),
                    if (c.isNull(9)) null else c.getString(9),
                    // Original imports sometimes omit cost. Preserve unknown, never fabricate.
                    !(kind in setOf("OPENING_BALANCE", "SNAPSHOT_ADJUSTMENT") &&
                        price == 0L && c.getLong(7) == 0L &&
                        note.contains("unknown", ignoreCase = true)))
            }
        }
        return rows
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

    /** Manual dated close/NAV only, preserving source and rejecting future data. */
    fun recordManualPrice(email: String, holdingId: String, priceMinor: Long,
        priceDate: String) = accounts.withUnlockedVault(email) { db ->
        require(priceMinor >= 0L)
        val day = LocalDate.parse(priceDate)
        require(!day.isAfter(LocalDate.now())) { "Future price is not allowed" }
        requireHoldingOwner(db, holdingId)
        val instrument = db.rawQuery("SELECT instrument_id FROM holding WHERE holding_id=?",
            arrayOf(holdingId)).use { c -> check(c.moveToFirst()); c.getString(0) }
        val timestamp = now()
        db.execSQL("INSERT INTO price_observation(instrument_id,price_date,basis,price_minor," +
            "source,ingested_at) VALUES(?,?,?,?,?,?)", arrayOf<Any>(
            instrument, day.toString(), "MANUAL", priceMinor, "USER_OVERRIDE", timestamp))
        db.execSQL("UPDATE holding SET current_price_minor=?,last_price_updated=?," +
            "price_source='USER_OVERRIDE' WHERE holding_id=?",
            arrayOf<Any>(priceMinor, day.toString(), holdingId))
    }

    companion object {
        /** Decimal input is converted once to exact scaled integers; never via Double. */
        fun parseMinor(display: String, scale: Int): Long =
            BigDecimal(display.trim()).movePointRight(scale).toBigIntegerExact().longValueExact()
        fun formatMinor(value: Long, scale: Int): String =
            BigDecimal(BigInteger.valueOf(value), scale).stripTrailingZeros().toPlainString()
    }
}
