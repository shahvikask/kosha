package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeMoneyWeightedReturnTest {
    private val owner = "synthetic-owner"
    private val holding = "synthetic-holding"
    private fun event(id: String, type: String, day: String, units: Long,
        price: Long, amount: Long, charges: Long = 0L,
        supersedes: String? = null, knownCost: Boolean = true) =
        NativePortfolioMath.Event(id, owner, holding, type, day, units, price,
            amount, charges, supersedes, knownCost)

    @Test fun oneYearBuyAndTerminalValueProduceActualMoneyWeightedReturn() {
        val events = listOf(event("buy", "BUY", "2024-01-01",
            100_000_000L, 10_000L, 10_000L))
        val rate = NativeMoneyWeightedReturn.forHolding(events, "EQUITY",
            11_000L, LocalDate.parse("2025-01-01"))
        assertNotNull(rate)
        assertEquals(9.98, rate!!, .2) // 366 days and 365.25-day year basis
    }

    @Test fun noInventedFlowForStatementOpeningOrUnknownCost() {
        val source = event("opening", "OPENING_BALANCE", "2024-01-01",
            100_000_000L, 10_000L, 10_000L)
        assertNull(NativeMoneyWeightedReturn.forHolding(listOf(source),
            "EQUITY", 20_000L, LocalDate.parse("2025-01-01")))
        assertNull(NativeMoneyWeightedReturn.forHolding(listOf(
            source.copy(knownCost = false)), "EQUITY",
            20_000L, LocalDate.parse("2025-01-01")))
    }

    @Test fun correctionChangesCashFlowExactlyOnceWithoutMutatingLedger() {
        val originals = listOf(
            event("old-buy", "BUY", "2024-01-01", 100_000_000L, 10_000L, 10_000L),
            event("correction", "CORRECTION", "2024-01-01",
                100_000_000L, 12_000L, 12_000L, supersedes = "old-buy")
        )
        val flows = NativeMoneyWeightedReturn.cashFlows(originals, 15_000L,
            LocalDate.parse("2025-01-01"))
        assertEquals(2, flows.size)
        assertEquals(-12_000L, flows.first().amountMinor)
        assertEquals(15_000L, flows.last().amountMinor)
        val result = NativeMoneyWeightedReturn.forHolding(originals, "EQUITY",
            15_000L, LocalDate.parse("2025-01-01"))
        assertNotNull(result)
        assertTrue(result!! > 20.0 && result < 30.0)
    }

    @Test fun distributionsAndSaleAreCashInflowsAndChargesAreNotIgnored() {
        val events = listOf(
            event("buy", "BUY", "2024-01-01", 200_000_000L,
                10_000L, 20_000L, charges = 40L),
            event("distribution", "DIVIDEND", "2024-06-01", 0L, 0L, 500L),
            event("sale", "SELL", "2024-09-01", 100_000_000L,
                12_000L, 12_000L, charges = 20L)
        )
        val flows = NativeMoneyWeightedReturn.cashFlows(events, 13_000L,
            LocalDate.parse("2025-01-01"))
        assertEquals(listOf(-20_040L, 500L, 11_980L, 13_000L),
            flows.map { it.amountMinor })
        assertNotNull(NativeMoneyWeightedReturn.forHolding(events, "EQUITY",
            13_000L, LocalDate.parse("2025-01-01")))
    }

    @Test fun sameDayOrSingleSignOrInvalidHistoryIsNotAPretendReturn() {
        assertNull(NativeMoneyWeightedReturn.percent(listOf(
            NativeMoneyWeightedReturn.Flow(LocalDate.parse("2025-01-01"), -100L),
            NativeMoneyWeightedReturn.Flow(LocalDate.parse("2025-01-01"), 110L))))
        assertNull(NativeMoneyWeightedReturn.percent(listOf(
            NativeMoneyWeightedReturn.Flow(LocalDate.parse("2025-01-01"), 100L),
            NativeMoneyWeightedReturn.Flow(LocalDate.parse("2025-06-01"), 200L))))
        val bad = event("sell", "SELL", "2025-01-01", 100_000_000L,
            10_000L, 10_000L)
        assertNull(NativeMoneyWeightedReturn.forHolding(listOf(bad),
            "EQUITY", 10_000L, LocalDate.parse("2025-10-01")))
        assertThrows(IllegalArgumentException::class.java) {
            NativeMoneyWeightedReturn.cashFlows(listOf(
                event("future", "BUY", "2099-01-01", 100_000_000L,
                    10_000L, 10_000L)), 10_000L, LocalDate.parse("2025-01-01"))
        }
    }
}
