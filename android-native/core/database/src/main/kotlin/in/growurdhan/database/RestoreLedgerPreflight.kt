package `in`.growurdhan.database

import java.time.LocalDate

/**
 * Pure, non-mutating preflight for frozen snapshot -> native ledger conversion.
 * It cannot authorize a restore, write rows or discard source JSON. Callers
 * must preserve the original authenticated JSON byte-for-byte for opaque fields.
 */
object RestoreLedgerPreflight {
    private const val MAX_RECORDS = 250_000
    private val DECIMAL = Regex("-?(0|[1-9][0-9]*)")
    private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    val EVENT_TYPES = setOf("OPENING_BALANCE", "SNAPSHOT_ADJUSTMENT", "BUY", "SELL",
        "BONUS", "SPLIT", "DIVIDEND", "INTEREST", "VALUATION_UPDATE",
        "REPAYMENT", "CORRECTION")

    data class Owner(val id: String)
    data class Holding(val id: String, val ownerId: String, val accountId: String? = null,
                       val quantityScaled: String, val currentPriceMinor: String)
    data class Account(val id: String, val ownerId: String)
    data class Goal(val id: String, val ownerId: String?)
    data class Event(val id: String, val holdingId: String, val ownerId: String,
                     val eventType: String, val eventDate: String,
                     val quantityScaled: String, val unitPriceMinor: String,
                     val totalAmountMinor: String, val chargesMinor: String = "0",
                     val supersedesEventId: String? = null)
    data class Snapshot(val owners: List<Owner>, val holdings: List<Holding>,
                        val accounts: List<Account>, val goals: List<Goal>,
                        val events: List<Event>)
    data class Counts(val owners: Int, val holdings: Int, val accounts: Int,
                      val goals: Int, val events: Int)

    /** Reject incomplete snapshots, invalid links and JS-number coercion before BEGIN. */
    fun validate(s: Snapshot): Counts {
        require(s.owners.isNotEmpty()) { "Restore has no owners" }
        require(listOf(s.owners.size,s.holdings.size,s.accounts.size,s.goals.size,s.events.size)
            .all { it <= MAX_RECORDS }) { "Restore exceeds bounded record count" }
        fun unique(ids: List<String>): Set<String> {
            require(ids.all { it.isNotBlank() && it.length <= 128 }) { "Missing or oversized ID" }
            val result = ids.toSet()
            require(result.size == ids.size) { "Duplicate restore ID" }
            return result
        }
        fun scaled(text: String) {
            require(text.length in 1..20 && DECIMAL.matches(text) &&
                text.toLongOrNull() != null) { "Unsafe monetary or quantity integer" }
        }
        val owners = unique(s.owners.map { it.id })
        val holdings = unique(s.holdings.map { it.id })
        val accounts = unique(s.accounts.map { it.id })
        val accountOwners = s.accounts.associate { it.id to it.ownerId }
        val holdingOwners = s.holdings.associate { it.id to it.ownerId }
        unique(s.goals.map { it.id })
        val events = unique(s.events.map { it.id })
        val eventById = s.events.associateBy { it.id }
        // Multiple corrections of a single historical event are ambiguous:
        // the source lot engine would omit the old event and count BOTH
        // competing replacements. Reject without editing source records.
        val correctedTargets = HashSet<String>()
        for (a in s.accounts) require(a.ownerId in owners) { "Unknown account owner" }
        for (h in s.holdings) {
            require(h.ownerId in owners) { "Unknown holding owner" }
            if (h.accountId != null) require(h.accountId in accounts &&
                accountOwners[h.accountId] == h.ownerId) { "Invalid holding account ownership" }
            scaled(h.quantityScaled)
            scaled(h.currentPriceMinor)
        }
        for (g in s.goals) if (g.ownerId != null)
            require(g.ownerId in owners) { "Unknown goal owner" }
        // Frozen src/lib/positions.ts::derivePositions uses a transient
        // openingBalanceFor() when a stored positive holding has NO ledger
        // events. Native v9 persists quantity only in ledger_event, not
        // holding. Importing this unchanged source would silently lose the
        // position, while inventing a BUY/OPENING_BALANCE during restore
        // would invent financial history. Refuse until an owner-reviewed,
        // exact-source migration contract supports that exceptional case.
        val eventHoldingIds = s.events.mapTo(HashSet()) { it.holdingId }
        for (h in s.holdings) {
            require(h.quantityScaled.toLong() <= 0L || h.id in eventHoldingIds) {
                "Positive stored holding has no ledger; cannot restore without invented event"
            }
        }
        for (e in s.events) {
            require(e.holdingId in holdings && e.ownerId in owners) { "Orphan ledger event" }
            require(holdingOwners[e.holdingId] == e.ownerId) {
                "Ledger owner differs from holding owner"
            }
            require(e.eventType in EVENT_TYPES && ISO_DATE.matches(e.eventDate) &&
                runCatching { LocalDate.parse(e.eventDate) }.isSuccess) {
                "Unknown event type or malformed event date"
            }
            scaled(e.quantityScaled)
            scaled(e.unitPriceMinor)
            scaled(e.totalAmountMinor)
            scaled(e.chargesMinor)
            // Must match frozen ck_event__correction: no fabricated correction
            // reference and no ordinary event claiming to supersede history.
            require((e.eventType == "CORRECTION") ==
                (e.supersedesEventId != null)) { "Invalid correction linkage" }
            if (e.supersedesEventId != null) {
                require(e.supersedesEventId != e.id && e.supersedesEventId in events) {
                    "Invalid correction linkage"
                }
                val original = eventById.getValue(e.supersedesEventId)
                require(original.ownerId == e.ownerId &&
                    original.holdingId == e.holdingId) {
                    "Correction cannot alter another owner or holding"
                }
                require(correctedTargets.add(e.supersedesEventId)) {
                    "Competing corrections of one source event"
                }
            }
        }
        // A chain such as CORRECTION C -> CORRECTION B -> BUY A is legal.
        // A directed cycle has no underlying economic event and causes
        // source/native ledger folding to lose or misinterpret acquisitions.
        // Iterative tri-colour walk is O(event count), avoiding recursion
        // overflow for large genuine family ledgers (up to MAX_RECORDS).
        val state = HashMap<String, Int>()
        for (origin in s.events) {
            if (origin.eventType != "CORRECTION" || state[origin.id] == 2) continue
            var current = origin
            val walking = ArrayList<String>()
            while (current.eventType == "CORRECTION" && state[current.id] != 2) {
                require(state[current.id] != 1) { "Circular correction lineage" }
                state[current.id] = 1
                walking += current.id
                current = eventById.getValue(requireNotNull(current.supersedesEventId))
            }
            for (id in walking) state[id] = 2
        }
        // The frozen application deliberately does not derive units from
        // dividends, interest, cash repayments, or valuation updates. A
        // positive stored balance accompanied ONLY by those records would
        // silently become a zero-unit native holding. Do not invent an
        // OPENING_BALANCE to hide missing historical acquisition evidence.
        val quantitySources = HashSet<String>()
        val acquisitionKinds = setOf("BUY", "OPENING_BALANCE",
            "BONUS", "SNAPSHOT_ADJUSTMENT")
        for (event in s.events) {
            if (event.id in correctedTargets) continue
            var source = event
            while (source.eventType == "CORRECTION") {
                source = eventById.getValue(requireNotNull(source.supersedesEventId))
            }
            if (source.eventType in acquisitionKinds &&
                event.quantityScaled.toLong() > 0L) {
                quantitySources += event.holdingId
            }
        }
        for (h in s.holdings) {
            require(h.quantityScaled.toLong() <= 0L || h.id in quantitySources) {
                "Positive historical balance lacks active acquisition or snapshot evidence"
            }
        }
        return Counts(s.owners.size, s.holdings.size, s.accounts.size, s.goals.size, s.events.size)
    }
}
