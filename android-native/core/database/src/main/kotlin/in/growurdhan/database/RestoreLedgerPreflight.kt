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
            if (e.supersedesEventId != null) {
                require(e.supersedesEventId != e.id && e.supersedesEventId in events) {
                    "Invalid correction linkage"
                }
            }
        }
        return Counts(s.owners.size, s.holdings.size, s.accounts.size, s.goals.size, s.events.size)
    }
}
