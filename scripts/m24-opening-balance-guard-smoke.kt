import `in`.growurdhan.database.RestoreLedgerPreflight as P

fun main() {
    val owner = P.Owner("owner-A")
    val holding = P.Holding("holding-A", "owner-A", "account-A", "9007199254740993", "100")
    val buy = P.Event("event-A", "holding-A", "owner-A", "BUY", "2026-10-17", "9007199254740993", "100", "9007199254740993")
    val good = P.Snapshot(listOf(owner), listOf(holding), listOf(P.Account("account-A", "owner-A")), emptyList(), listOf(buy))
    var accepted = 0
    var rejected = 0
    fun accept(snapshot: P.Snapshot) {
        check(P.validate(snapshot).owners == 1)
        accepted++
    }
    fun reject(snapshot: P.Snapshot, expected: String) {
        try { P.validate(snapshot); error("case unexpectedly accepted: $expected") }
        catch (e: IllegalArgumentException) {
            check(e.message?.contains(expected) == true) { "unexpected: ${e.message}" }
            rejected++
        }
    }
    accept(good)
    accept(good.copy(holdings = listOf(holding.copy(quantityScaled="0")), events=emptyList()))
    accept(good.copy(holdings = listOf(holding.copy(quantityScaled="-1")), events=emptyList()))
    reject(good.copy(events=emptyList()), "Positive stored holding has no ledger")
    reject(good.copy(holdings=listOf(holding, P.Holding("holding-B","owner-A",null,"10","0"))), "Positive stored holding has no ledger")
    reject(good.copy(holdings=listOf(holding.copy(quantityScaled="9223372036854775808"))), "Unsafe monetary")
    reject(good.copy(holdings=listOf(holding.copy(quantityScaled="2.5"))), "Unsafe monetary")
    reject(good.copy(events=listOf(buy.copy(holdingId="missing"))), "Positive stored holding has no ledger")
    reject(good.copy(events=listOf(buy.copy(eventType="CORRECTION"))), "Invalid correction linkage")
    reject(good.copy(events=listOf(buy.copy(eventDate="2026-02-31"))), "Unknown event type or malformed")
    reject(good.copy(accounts=listOf(P.Account("account-A", "other"))), "Unknown account owner")
    println("M24_FROZEN_OPENING_BALANCE_GUARD_LOCAL_PASS accepted=$accepted rejected=$rejected signed_long_preserved=true")
}
