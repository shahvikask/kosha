package `in`.growurdhan.database

import java.security.MessageDigest

/** Strict frozen-row projection; no SQLCipher I/O, credentials, or synthesized ledger events. */
object RestoreV9Plan {
    data class Row(val table: String, val columns: List<String>, val values: List<Any?>) {
        init { require(columns.isNotEmpty() && columns.size == values.size) }
    }
    data class Family(val id: String, val name: String, val relation: String,
        val isPrimary: Boolean, val color: String?, val panMasked: String? = null,
        val panForStatements: String? = null, val dateOfBirth: String? = null,
        val notes: String? = null)
    data class Account(val id: String, val ownerId: String, val accountType: String,
        val custodian: String, val identifierMasked: String? = null,
        val dpId: String? = null, val clientId: String? = null,
        val boId: String? = null, val nominee: String? = null)
    /** Restorable source metadata; null means absent in original authenticated source. */
    data class HoldingMetadata(
        val benchmarkPriceMinor: String? = null, val issuePriceMinor: String? = null,
        val couponRateBps: Long? = null, val interestRateBps: Long? = null,
        val nextCouponDate: String? = null, val maturityDate: String? = null,
        val institutionName: String? = null, val accountNumberMasked: String? = null,
        val monthlyEmiMinor: String? = null, val emiStatus: String? = null,
        val remainingEmiCount: Long? = null, val acquiredOn: String? = null,
        val detailsJson: String? = null, val reviewReasonsJson: String? = null,
        val tagsJson: String? = null, val location: String? = null,
        val tag: String? = null, val fmv31Jan2018Minor: String? = null,
        val exposureNode: String? = null, val l2Node: String? = null,
        val wrapper: String? = null, val allocationSplitJson: String? = null,
        val amc: String? = null, val rta: String? = null,
        val amfiCode: String? = null, val advisor: String? = null,
        val schemeType: String? = null, val ucc: String? = null,
        val exchange: String? = null, val dpName: String? = null
    )
    data class Holding(val id: String, val ownerId: String, val accountId: String?,
        val name: String, val category: String, val subCategory: String,
        val symbolOrFolio: String, val quantityScaled: String, val currentPriceMinor: String,
        val lastPriceUpdated: String, val priceSource: String,
        val isin: String? = null, val notes: String? = null,
        val needsReview: Boolean? = null,
        val averageCostPerUnitMinor: String? = null,
        val metadata: HoldingMetadata = HoldingMetadata())
    data class Event(val id: String, val holdingId: String, val holdingName: String,
        val ownerId: String, val eventType: String, val eventDate: String,
        val quantityScaled: String, val unitPriceMinor: String, val totalAmountMinor: String,
        val chargesMinor: String, val createdAt: String, val supersedesEventId: String? = null,
        val importBatchId: String? = null, val sourceRef: String? = null,
        val notes: String? = null)
    data class Goal(val id: String, val ownerId: String?, val name: String,
        val targetMinor: String, val targetDate: String?, val createdAt: String,
        val priority: String? = null)
    data class Login(val email: String, val firstName: String, val lastName: String,
        val passwordSalt: String, val passwordHash: String, val passwordIterations: Int,
        val passwordChangedAt: String, val q1Id: String, val q1Hash: String,
        val q2Id: String, val q2Hash: String, val createdAt: String,
        val panMasked: String? = null, val panForStatements: String? = null,
        val dateOfBirth: String? = null)
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
            "created_at" to l.createdAt, "pan_masked" to l.panMasked,
            "pan_for_statements" to l.panForStatements,
            "date_of_birth" to l.dateOfBirth)
        for (f in s.families) {
            require(f.relation in relations)
            rows += row("entity", "entity_id" to f.id,
                "entity_type" to (if (f.relation == "HUF") "HUF" else "INDIVIDUAL"),
                "display_name" to required(f.name), "relation" to f.relation,
                "is_primary" to (if (f.isPrimary) 1L else 0L),
                "color" to f.color, "notes" to f.notes, "pan_masked" to f.panMasked,
                "pan_for_statements" to f.panForStatements,
                "date_of_birth" to f.dateOfBirth,
                // Frozen FamilyMember has no creation timestamp; this is the
                // authenticated snapshot date, with the original JSON retained.
                "created_at" to s.exportedAt)
        }
        for (a in s.accounts) rows += row("account", "account_id" to a.id,
            "entity_id" to a.ownerId, "account_type" to required(a.accountType),
            "custodian" to required(a.custodian), "identifier_masked" to a.identifierMasked,
            "dp_id" to a.dpId, "client_id" to a.clientId,
            "bo_id" to a.boId, "nominee" to a.nominee,
            "created_at" to s.exportedAt)
        for (h in s.holdings) {
            require(h.category in categories)
            required(h.name)
            required(h.lastPriceUpdated)
            required(h.priceSource)
            val instrument = "legacy-instrument:" + h.id
            val m = h.metadata
            require(m.remainingEmiCount == null || m.remainingEmiCount in 0..3600)
            require(m.couponRateBps == null || m.couponRateBps in 0..100_000)
            require(m.interestRateBps == null || m.interestRateBps in 0..100_000)
            require(m.emiStatus == null || m.emiStatus in setOf("PAYING", "PAUSED", "STOPPED"))
            rows += row("instrument", "instrument_id" to instrument,
                "name" to h.name, "category" to h.category,
                "sub_category" to h.subCategory, "symbol_or_folio" to h.symbolOrFolio,
                "isin" to h.isin, "created_at" to s.exportedAt,
                "fmv_31jan2018_minor" to m.fmv31Jan2018Minor?.let(::money),
                "exposure_node" to m.exposureNode, "l2_node" to m.l2Node,
                "wrapper" to m.wrapper, "allocation_split" to m.allocationSplitJson,
                "amc" to m.amc, "rta" to m.rta, "amfi_code" to m.amfiCode,
                "advisor" to m.advisor, "scheme_type" to m.schemeType,
                "ucc" to m.ucc, "exchange" to m.exchange, "dp_name" to m.dpName)
            rows += row("holding", "holding_id" to h.id, "instrument_id" to instrument,
                "entity_id" to h.ownerId, "account_id" to h.accountId,
                "current_price_minor" to money(h.currentPriceMinor),
                "benchmark_price_minor" to m.benchmarkPriceMinor?.let(::money),
                "issue_price_minor" to m.issuePriceMinor?.let(::money),
                "coupon_rate_bps" to m.couponRateBps,
                "interest_rate_bps" to m.interestRateBps,
                "next_coupon_date" to m.nextCouponDate, "maturity_date" to m.maturityDate,
                "institution_name" to m.institutionName,
                "account_number_masked" to m.accountNumberMasked,
                "monthly_emi_minor" to m.monthlyEmiMinor?.let(::money),
                "emi_status" to m.emiStatus, "remaining_emi_count" to m.remainingEmiCount,
                "acquired_on" to m.acquiredOn, "details_json" to m.detailsJson,
                "review_reasons" to m.reviewReasonsJson, "tags_json" to m.tagsJson,
                "location" to m.location, "tag" to m.tag,
                "needs_review" to (if (h.needsReview == true) 1L else 0L),
                "notes" to h.notes,
                "last_price_updated" to h.lastPriceUpdated,
                "price_source" to h.priceSource, "created_at" to s.exportedAt)
            if (h.averageCostPerUnitMinor != null) {
                val sourceCost = money(h.averageCostPerUnitMinor)
                // Opaque historic cost per unit, never a synthetic money event.
                rows += row("setting", "key" to
                    ("m24.frozen_average_cost_per_unit_minor." + h.id),
                    "value" to sourceCost.toString())
            }
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
                "import_batch_id" to e.importBatchId, "source_ref" to e.sourceRef,
                "notes" to e.notes, "entered_at" to required(e.createdAt))
        }
        for (g in s.goals) rows += row("goal", "goal_id" to g.id,
            "entity_id" to g.ownerId, "name" to required(g.name),
            "target_minor" to money(g.targetMinor), "target_date" to g.targetDate,
            "priority" to g.priority, "created_at" to required(g.createdAt))
        rows += row("setting", "key" to "m24.authenticated_frozen_original_json",
            "value" to s.authenticatedOriginalJson)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(s.authenticatedOriginalJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return Plan(rows, digest)
    }
}
