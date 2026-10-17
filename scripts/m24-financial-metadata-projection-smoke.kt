import `in`.growurdhan.database.RestoreV9Plan as P

/** Standalone synthetic-only host JVM contract; no password/PAN/user holdings. */
fun main() {
    val snapshot = P.Snapshot(
        exportedAt = "2026-10-17T00:00:00Z",
        login = P.Login("synthetic@example.invalid", "Fixture", "User",
            "salt-only", "hash-only", 210_000, "2026-10-17",
            "city", "qhash1", "pet", "qhash2", "2026-10-17"),
        families = listOf(P.Family("owner", "Synthetic Owner", "Self", true, null)),
        accounts = listOf(P.Account("account", "owner", "DEMAT", "Custodian",
            dpId = "DP000123", clientId = "C123", boId = "BO123", nominee = "Test nominee")),
        holdings = listOf(P.Holding("holding", "owner", "account", "Synthetic instrument",
            "EQUITY", "Large cap", "SYMBOL", "9007199254740993", "200",
            "2026-10-17", "Test only", isin = "INE000SYNTH0",
            notes = "original holding note", needsReview = true)),
        events = listOf(P.Event("event", "holding", "Synthetic instrument", "owner",
            "BUY", "2026-10-17", "9007199254740993", "200",
            "9007199254740993", "25", "2026-10-17",
            importBatchId = "batch-original", sourceRef = "synthetic-ref",
            notes = "original ledger note")),
        goals = listOf(P.Goal("goal", "owner", "Synthetic goal",
            "9007199254740993", "2030-10-17", "2026-10-17", priority = "HIGH")),
        authenticatedOriginalJson = """{"kind":"GUDVAULT","opaque":{"nested":true}}"""
    )
    val plan = P.project(snapshot)
    check(plan.rows.size == 8)
    fun mapped(table: String, key: String): Any? {
        val row = plan.rows.single { it.table == table }
        return row.values[row.columns.indexOf(key).also { check(it >= 0) }]
    }
    val expected = listOf(
        Triple("account", "dp_id", "DP000123"),
        Triple("account", "client_id", "C123"),
        Triple("account", "bo_id", "BO123"),
        Triple("account", "nominee", "Test nominee"),
        Triple("instrument", "isin", "INE000SYNTH0"),
        Triple("holding", "notes", "original holding note"),
        Triple("ledger_event", "import_batch_id", "batch-original"),
        Triple("ledger_event", "source_ref", "synthetic-ref"),
        Triple("ledger_event", "notes", "original ledger note"),
        Triple("goal", "priority", "HIGH"),
    )
    for ((table, key, value) in expected) check(mapped(table, key) == value) {
        "$table.$key did not preserve synthetic source data"
    }
    check(mapped("holding", "needs_review") == 1L)
    for (key in listOf("quantity_scaled", "total_amount_minor"))
        check(mapped("ledger_event", key) == 9007199254740993L)
    check(plan.rows.count { it.table == "ledger_event" } == snapshot.events.size)
    check(mapped("setting", "value") == snapshot.authenticatedOriginalJson)
    var denied = 0
    fun refuse(s: P.Snapshot) {
        try { P.project(s) } catch (_: IllegalArgumentException) { denied++; return }
        error("Unsafe synthetic restore source unexpectedly projected")
    }
    refuse(snapshot.copy(holdings = snapshot.holdings.map { it.copy(quantityScaled = "9223372036854775808") }))
    refuse(snapshot.copy(events = emptyList()))
    refuse(snapshot.copy(events = snapshot.events.map { it.copy(eventType = "FAKE") }))
    refuse(snapshot.copy(accounts = snapshot.accounts.map { it.copy(ownerId = "stranger") }))
    check(denied == 4)
    println("M24_METADATA_PROJECTION_LOCAL_PASS mapped=11 largeIntegers=2 rejected=4 ledgerEvents=1 opaquePreserved=true")
}
