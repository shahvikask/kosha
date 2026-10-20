package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic authenticated historical snapshots only. These source tests are
 * not a genuine customer vault or an executed restore acceptance result.
 */
class RestorePositionParityTest {
    private fun holding(qty: String, category: String = "EQUITY") =
        RestoreV9Plan.Holding("holding-a", "owner-a", null,
            "Synthetic equity", category, "Other", "DEMO",
            qty, "12000", "2025-03-01", "USER_ENTRY")
    private fun event(id: String, kind: String, qty: String,
        date: String = "2024-01-01", unit: String = "10000",
        supersedes: String? = null): RestoreV9Plan.Event =
        RestoreV9Plan.Event(id, "holding-a", "Synthetic equity",
            "owner-a", kind, date, qty, unit, "0", "0",
            "2025-03-01", supersedesEventId = supersedes)
    private fun snapshot(qty: String, vararg events: RestoreV9Plan.Event) =
        RestoreV9Plan.Snapshot("2025-03-01T00:00:00Z",
            RestoreV9Plan.Login("restore@example.invalid", "Synthetic", "Family",
                "salt", "hash", 210000, "2025-01-01",
                "city", "answer1", "pet", "answer2", "2025-01-01"),
            listOf(RestoreV9Plan.Family("owner-a", "Synthetic", "Self",
                true, null)),
            emptyList(), listOf(holding(qty)), events.toList(),
            emptyList(), "{\"fixture\":\"not-a-customer-backup\"}")

    @Test fun exactUnchangedSourceQuantityIsAcceptedWithoutSynthesizingEvent() {
        val source = snapshot("100000000",
            event("buy-1", "BUY", "100000000"))
        RestorePositionParity.requireReconciled(source)
        val planned = RestoreV9Plan.project(source)
        assertEquals(1, planned.rows.count { it.table == "ledger_event" })
        assertEquals("buy-1",
            planned.rows.single { it.table == "ledger_event" }.values.first())
    }

    @Test fun correctionChainMustMatchItsFinalQuantityNotOriginalBuy() {
        val chain = arrayOf(
            event("buy-1", "BUY", "100000000"),
            event("correct-1", "CORRECTION", "150000000",
                supersedes = "buy-1"),
            event("correct-2", "CORRECTION", "120000000",
                supersedes = "correct-1"))
        RestorePositionParity.requireReconciled(snapshot("120000000", *chain))
        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(snapshot("100000000", *chain))
        }
    }

    @Test fun salesAndCorporateActionSplitMustReconcileExactScaledUnits() {
        val history = arrayOf(
            event("buy-1", "BUY", "200000000"),
            event("split", "SPLIT", "2", "2024-06-01", "1"),
            event("sale", "SELL", "100000000", "2024-09-01"))
        RestorePositionParity.requireReconciled(snapshot("300000000", *history))
        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(snapshot("200000000", *history))
        }
    }

    @Test fun overdrawnSaleIsNeverRestoredAsOtherwiseValidFinancialHistory() {
        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(snapshot("0",
                event("buy-1", "BUY", "100000000"),
                event("excess-sale", "SELL", "200000000", "2024-06-01")))
        }
    }

    @Test fun additionalCashFlowWithoutUnitChangesStillPreservesSourceUnits() {
        RestorePositionParity.requireReconciled(snapshot("100000000",
            event("buy-1", "BUY", "100000000"),
            event("dividend-1", "DIVIDEND", "0", "2024-07-01")))
    }

    @Test fun sourcePositionWithoutLedgerCannotBeFabricated() {
        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(snapshot("100000000"))
        }
        RestorePositionParity.requireReconciled(snapshot("0"))
    }
}
