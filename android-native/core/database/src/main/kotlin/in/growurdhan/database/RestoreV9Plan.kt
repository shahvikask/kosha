package `in`.growurdhan.database

import java.security.MessageDigest

/** Strict frozen-row projection; no SQLCipher I/O, credentials, or synthesized ledger events. */
object RestoreV9Plan {
    data class Row(val table: String, val columns: List<String>, val values: List<Any?>) {
        init { require(columns.isNotEmpty() && columns.size == values.size) }
    }
    data class Family(val id: String, val name: String, val relation: String,
        val isPrimary: Boolean, val color: String?, val panMasked: String? = null,
        val panForStatements: String? = null, val dateOfBirth: String? = null)
    data class Account(val id: String, val ownerId: String, val accountType: String,
        val custodian: String, val identifierMasked: String? = null)
    data class Holding(val id: String, val ownerId: String, val accountId: String?,
        val name: String, val category: String, val subCategory: String,
        val symbolOrFolio: String, val quantityScaled: String, val currentPriceMinor: String,
        val lastPriceUpdated: String, val priceSource: String)
    data class Event(val id: String, val holdingId: String, val holdingName: String,
        val ownerId: String, val eventType: String, val eventDate: String,
        val quantityScaled: String, val unitPriceMinor: String, val totalAmountMinor: String,
        val chargesMinor: String, val createdAt: String, val supersedesEventId: String? = null)
    data class Goal(val id: String, val ownerId: String?, val name: String,
        val targetMinor: String, val targetDate: String?, val createdAt: String)
    data class Login(val email: String, val firstName: String, val lastName: String,
        val passwordSalt: String, val passwordHash: String, val passwordIterations: Int,
        val passwordChangedAt: String, val q1Id: String, val q1Hash: String,
        val q2Id: String, val q2Hash: String, val createdAt: String)
    data class Snapshot(val exportedAt: String, val login: Login,
        val families: List<Family>, val accounts: List<Account>,
        val holdings: List<Holding>, val events: List<Event>, val goals: List<Goal>,
        val authenticatedOriginalJson: String)
    data class Plan(val rows: List<Row>, val originalJsonSha256: String)

    private val relations = setOf("Self", "Spouse", "Parent", "Child", "HUF", "Joint")
    private val categories = setOf("EQUITY", "MUTUAL_FUND", "GOLD_SGB", "FIXED_INCOME",
        "REAL_ESTATE", "SPECIALISED", "CASH_BANK", "LIABILITY")
    private fun required(s: String) = s.also { require(it.isNotBlank() && it.length <= 4096) {
        "Missing or invalid original field" } }
    private fun money(s: String): Long {
        require(s.matches(Regex("-?(0|[1-9][0-9]*)")) && s.length <= 20) {
            "Scaled integer is not canonical decimal text" }
        return s.toLongOrNull() ?: throw IllegalArgumentException("Scaled integer exceeds signed 64-bit")
    }
    private fun row(table: String, vararg fields: Pair<String, Any?>): Row =
        Row(table, fields.map { it.first }, fields.map { it.second })

    /** Only the authenticated, unchanged source JSON may be retained as opaque encrypted data. */
    fun project(s: Snapshot): Plan {
        require(s.authenticatedOriginalJson.toByteArray(Charsets.UTF_8).size in 1..64 * 1024 * 1024)
        required(s.exportedAt)
        val l = s.login
        for (v in listOf(l.email, l.firstName, l.lastName, l.passwordSalt, l.passwordHash,
                l.passwordChangedAt, l.q1Id, l.q1Hash, l.q2Id, l.q2Hash, l.createdAt)) required(v)
        require(l.passwordIterations in 100_000..1_000_000) { "Unsupported frozen account hashing" }
        require(s.families.count { it.isPrimary && it.relation == "Self" } == 1 &&
            s.families.count { it.isPrimary } == 1) { "Ambiguous household primary owner" }
        val graph = RestoreLedgerPreflight.Snapshot(
            s.families.map { RestoreLedgerPreflight.Owner(it.id) },
            s.holdings.map { RestoreLedgerPreflight.Holding(it.id, it.ownerId, it.accountId,
                it.quantityScaled, it.currentPriceMinor) },
            s.accounts.map { RestoreLedgerPreflight.Account(it.id, it.ownerId) },
            s.goals.map { RestoreLedgerPreflight.Goal(it.id, it.ownerId) },
            s.events.map { RestoreLedgerPreflight.Event(it.id, it.holdingId, it.ownerId,
                it.eventType, it.eventDate, it.quantityScaled, it.unitPriceMinor,
                it.totalAmountMinor, it.chargesMinor, it.supersedesEventId) }
        )
        RestoreLedgerPreflight.validate(graph)
        val rows = ArrayList<Row>()
        rows += row("vault_account", "id" to "legacy-authenticated-account", "email" to l.email,
            "first_name" to l.firstName, "last_name" to l.lastName,
            "password_salt" to l.passwordSalt, "password_hash" to l.passwordHash,
            "password_iterations" to l.passwordIterations.toLong(),
            "password_changed_at" to l.passwordChangedAt, "q1_id" to l.q1Id,
            "q1_hash" to l.q1Hash, "q2_id" to l.q2Id, "q2_hash" to l.q2Hash,
            "created_at" to l.createdAt)
        for (f in s.families) {
            require(f.relation in relations)
            rows += row("entity", "entity_id" to f.id,
                "entity_type" to (if (f.relation == "HUF") "HUF" else "INDIVIDUAL"),
                "display_name" to required(f.name), "relation" to f.relation,
                "is_primary" to (if (f.isPrimary) 1L else 0L),
                "color" to f.color, "pan_masked" to f.panMasked,
                "pan_for_statements" to f.panForStatements,
                "date_of_birth" to f.dateOfBirth,
                // Frozen FamilyMember has no creation timestamp; this is the
                // authenticated snapshot date, with the original JSON retained.
                "created_at" to s.exportedAt)
        }
        for (a in s.accounts) rows += row("account", "account_id" to a.id,
            "entity_id" to a.ownerId, "account_type" to required(a.accountType),
            "custodian" to required(a.custodian), "identifier_masked" to a.identifierMasked,
            "created_at" to s.exportedAt)
        for (h in s.holdings) {
            require(h.category in categories)
            required(h.name)
            required(h.lastPriceUpdated)
            required(h.priceSource)
            val instrument = "legacy-instrument:" + h.id
            rows += row("instrument", "instrument_id" to instrument,
                "name" to h.name, "category" to h.category,
                "sub_category" to h.subCategory, "symbol_or_folio" to h.symbolOrFolio,
                "created_at" to s.exportedAt)
            rows += row("holding", "holding_id" to h.id, "instrument_id" to instrument,
                "entity_id" to h.ownerId, "account_id" to h.accountId,
                "current_price_minor" to money(h.currentPriceMinor),
                "last_price_updated" to h.lastPriceUpdated,
                "price_source" to h.priceSource, "created_at" to s.exportedAt)
        }
        for (e in s.events) {
            require(s.holdings.any { it.id == e.holdingId && it.name == e.holdingName }) {
                "Ledger holding name mismatches authenticated holding" }
            rows += row("ledger_event", "ledger_event_id" to e.id, "entity_id" to e.ownerId,
                "holding_id" to e.holdingId, "holding_name" to required(e.holdingName),
                "event_type" to e.eventType, "trade_date" to e.eventDate,
                "quantity_scaled" to money(e.quantityScaled),
                "unit_price_minor" to money(e.unitPriceMinor),
                "total_amount_minor" to money(e.totalAmountMinor),
                "charges_minor" to money(e.chargesMinor),
                "supersedes_event_id" to e.supersedesEventId,
                "entered_at" to required(e.createdAt))
        }
        for (g in s.goals) rows += row("goal", "goal_id" to g.id,
            "entity_id" to g.ownerId, "name" to required(g.name),
            "target_minor" to money(g.targetMinor), "target_date" to g.targetDate,
            "created_at" to required(g.createdAt))
        rows += row("setting", "key" to "m24.authenticated_frozen_original_json",
            "value" to s.authenticatedOriginalJson)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(s.authenticatedOriginalJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return Plan(rows, digest)
    }
}
