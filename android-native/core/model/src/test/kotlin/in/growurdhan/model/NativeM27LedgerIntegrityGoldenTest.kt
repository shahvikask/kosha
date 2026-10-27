package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

/** Frozen lots.ts sort/date/parcel logic plus native fees and provenance contract. */
class NativeM27LedgerIntegrityGoldenTest {
    private fun event(id: String, kind: String, day: String, qty: Long, unit: Long,
        fee: Long = 0L, replace: String? = null, owner: String = "first",
        known: Boolean = true): NativePortfolioMath.Event =
        NativePortfolioMath.Event(id, owner, "holding", kind, day,
            qty, unit, 0L, fee, replace, known)

    @Test fun sameDayPurchaseSplitSaleUsesDeterministicFifoAndEveryScaledMinorOfFees() {
        val events = listOf(
            event("sell", "SELL", "2025-01-15", 250_000_000L, 60_000L, fee = 101L),
            event("split", "SPLIT", "2025-01-15", 3L, 2L),
            event("b", "BUY", "2025-01-15", 200_000_000L, 40_000L, fee = 200L),
            event("a", "BUY", "2025-01-15", 100_000_000L, 30_000L, fee = 100L))
        val fold = NativePortfolioMath.fold(events, "EQUITY")
        assertTrue(fold.errors.toString(), fold.errors.isEmpty())
        assertEquals(200_000_000L, fold.quantityScaled)
        assertEquals(53_467L, fold.knownCostMinor)
        assertEquals(listOf("a", "b"), fold.disposals.map { it.purchaseId })
        assertEquals(listOf(59_840L, 33_226L), fold.disposals.map { it.gainMinor })
        assertEquals(101L, 150_000L - fold.disposals.sumOf { it.proceedsMinor })
    }

    @Test fun multiStepSameDayCorrectionSupersedesOldLotWithoutCreatingNewOwnerTrade() {
        val result = NativePortfolioMath.fold(listOf(
            event("original", "BUY", "2024-03-10", 100_000_000L, 10_000L),
            event("edit-a", "CORRECTION", "2024-03-10", 200_000_000L, 20_000L,
                replace = "original"),
            event("edit-b", "CORRECTION", "2024-03-10", 200_000_000L, 21_000L,
                replace = "edit-a"),
            event("sale", "SELL", "2024-03-10", 100_000_000L, 30_000L, fee = 25L)
        ), "EQUITY")
        assertTrue(result.errors.isEmpty())
        assertEquals(100_000_000L, result.quantityScaled)
        assertEquals(21_000L, result.knownCostMinor)
        assertEquals("edit-b", result.disposals.single().purchaseId)
        assertEquals(8_975L, result.disposals.single().gainMinor)
    }

    @Test fun unknownStatementOpeningNeverBecomesKnownFifoGain() {
        val result = NativePortfolioMath.fold(listOf(
            event("statement", "SNAPSHOT_ADJUSTMENT", "2024-03-31",
                200_000_000L, 0L, known = false),
            event("sale", "SELL", "2024-04-01", 50_000_000L, 20_000L, fee = 13L)
        ), "EQUITY")
        assertTrue(result.errors.isEmpty())
        assertTrue(result.hasUnknownCost)
        assertNull(result.disposals.single().costMinor)
        assertNull(result.disposals.single().gainMinor)
        assertEquals(150_000_000L, result.quantityScaled)
    }

    @Test fun foreignOwnerCannotBeFifoLotOrSupersessionTarget() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePortfolioMath.fold(listOf(
                event("self", "BUY", "2024-01-01", 100_000_000L, 10_000L),
                event("foreign", "BUY", "2024-01-01", 100_000_000L, 10_000L,
                    owner = "other")), "EQUITY")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePortfolioMath.fold(listOf(
                event("self", "BUY", "2024-01-01", 100_000_000L, 10_000L),
                event("foreign-edit", "CORRECTION", "2024-01-01",
                    100_000_000L, 10_000L, replace = "self", owner = "other")),
                "EQUITY")
        }
    }

    @Test fun invalidOversellAndUnrepresentableSplitNeverPassAsValidPositions() {
        val over = NativePortfolioMath.fold(listOf(
            event("buy", "BUY", "2024-01-01", 100_000_000L, 10_000L),
            event("sell", "SELL", "2024-02-01", 200_000_000L, 11_000L)
        ), "EQUITY")
        assertTrue(over.errors.any { it.contains("Sale exceeds available units") })
        assertThrows(IllegalArgumentException::class.java) {
            NativePortfolioMath.fold(listOf(
                event("tiny", "BUY", "2024-01-01", 1L, 10_000L),
                event("split", "SPLIT", "2024-02-01", 3L, 2L)),
                "EQUITY")
        }
    }

    @Test fun acquisitionProvenanceRequiresActualEvidenceNotStatementQuote() {
        assertFalse(NativeCostProvenance.knownCost(
            "SNAPSHOT_ADJUSTMENT", 27_500L, 27_500L, "Legacy registrar quote", null))
        assertFalse(NativeCostProvenance.knownCost(
            "SNAPSHOT_ADJUSTMENT", 0L, 0L, "", "UNKNOWN"))
        assertTrue(NativeCostProvenance.knownCost(
            "SNAPSHOT_ADJUSTMENT", 27_500L, 27_500L, "", "KNOWN"))
        assertFalse(NativeCostProvenance.knownCost(
            "OPENING_BALANCE", 0L, 0L, "", null))
        assertTrue(NativeCostProvenance.knownCost(
            "OPENING_BALANCE", 0L, 0L, "", "KNOWN"))
        assertThrows(IllegalArgumentException::class.java) {
            NativeCostProvenance.knownCost(
                "SNAPSHOT_ADJUSTMENT", 27_500L, 27_500L, "", "bad-provenance")
        }
    }
}
