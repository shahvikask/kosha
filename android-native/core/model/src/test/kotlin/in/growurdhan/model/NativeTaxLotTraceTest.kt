package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeTaxLotTraceTest {
    private fun event(id: String, kind: String, date: String, qty: Long,
        price: Long = 10_000L, known: Boolean = true, supersedes: String? = null) =
        NativePortfolioMath.Event(id, "synthetic-owner", "synthetic-holding",
            kind, date, qty, price, 0L, 0L, supersedes, known)

    @Test fun assessmentYearFollowsIndianAprilMarchBoundary() {
        assertEquals("AY25-26",
            NativeTaxLotTrace.assessmentYear(LocalDate.parse("2025-03-31")))
        assertEquals("AY26-27",
            NativeTaxLotTrace.assessmentYear(LocalDate.parse("2025-04-01")))
    }

    @Test fun tracesSaleToItsActualPurchaseWithoutApplyingInventedStatutoryRate() {
        val events = listOf(
            event("original", "BUY", "2023-01-01", 200_000_000L),
            event("sale", "SELL", "2025-03-10", 100_000_000L, 14_000L))
        val paper = NativeTaxLotTrace.trace(events, "EQUITY", "AY25-26")
        assertEquals(1, paper.items.size)
        val part = paper.items.single()
        assertEquals("original", part.purchaseEventId)
        assertEquals("sale", part.saleEventId)
        assertEquals(100_000_000L, part.quantityScaled)
        assertEquals(14_000L, part.proceedsMinor)
        assertEquals(10_000L, part.costMinor)
        assertEquals(4_000L, paper.fullyCostedGainMinor)
        assertFalse(paper.missingCost)
        assertTrue(paper.needsTaxRuleReview)
        assertTrue(NativeTaxLotTrace.trace(events, "EQUITY", "AY26-27").items.isEmpty())
    }

    @Test fun unknownCostNeverBecomesZeroBasisOrTaxableGainEstimate() {
        val events = listOf(
            event("unknown-opening", "OPENING_BALANCE", "2024-01-01",
                100_000_000L, 0L, false),
            event("sale", "SELL", "2025-05-01", 100_000_000L, 14_000L))
        val paper = NativeTaxLotTrace.trace(events, "EQUITY", "AY26-27")
        assertTrue(paper.missingCost)
        assertNull(paper.items.single().costMinor)
        assertNull(paper.items.single().realizedGainMinor)
        assertNull(paper.fullyCostedGainMinor)
    }

    @Test fun finalCorrectionIsUsedOnceAndBadSalesAreRejected() {
        val corrected = listOf(
            event("buy", "BUY", "2024-01-01", 100_000_000L),
            event("replacement", "CORRECTION", "2024-01-01", 100_000_000L,
                12_000L, true, "buy"),
            event("sale", "SELL", "2025-06-10", 100_000_000L, 15_000L))
        val result = NativeTaxLotTrace.trace(corrected, "EQUITY", "AY26-27")
        assertEquals(1, result.items.size)
        assertEquals("replacement", result.items.single().purchaseEventId)
        assertEquals(3_000L, result.fullyCostedGainMinor)
        assertThrows(IllegalArgumentException::class.java) {
            NativeTaxLotTrace.trace(listOf(
                event("excess", "SELL", "2025-06-10", 100_000_000L)),
                "EQUITY", "AY26-27")
        }
    }

    @Test fun invalidAssessmentYearIsNeverGuessed() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTaxLotTrace.trace(emptyList(), "EQUITY", "AY28-2029")
        }
    }
}
