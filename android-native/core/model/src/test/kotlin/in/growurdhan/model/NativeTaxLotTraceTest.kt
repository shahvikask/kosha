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

    @Test fun twoFifoLotsSourceGoldenFeeAndDayBoundary() {
        val events = listOf(
            NativePortfolioMath.Event("sale", "owner", "holding", "SELL",
                "2025-06-15", 200_000_000L, 15_000L, 30_000L, 101L),
            NativePortfolioMath.Event("late", "owner", "holding", "BUY",
                "2025-02-01", 200_000_000L, 12_000L, 24_000L, 400L),
            NativePortfolioMath.Event("early", "owner", "holding", "BUY",
                "2024-01-01", 100_000_000L, 10_000L, 10_000L, 250L))
        val before = events.toList()
        val paper = NativeTaxLotTrace.trace(events, "EQUITY", "AY26-27")
        assertEquals(before, events)
        assertEquals(listOf("early", "late"), paper.items.map { it.purchaseEventId })
        assertEquals(listOf(14_950L, 14_949L), paper.items.map { it.proceedsMinor })
        assertEquals(listOf(10_250L, 12_200L), paper.items.map { it.costMinor })
        assertEquals(listOf(4_700L, 2_749L), paper.items.map { it.realizedGainMinor })
        assertEquals(listOf(true, false), paper.items.map { it.ledgerLongTerm })
        assertEquals(7_449L, paper.fullyCostedGainMinor)
        assertTrue(paper.needsTaxRuleReview)
        assertEquals(101L, 30_000L - paper.items.sumOf { it.proceedsMinor })
        val open = NativePortfolioMath.fold(events, "EQUITY")
        assertTrue(open.errors.isEmpty())
        assertEquals(100_000_000L, open.quantityScaled)
        assertEquals(12_200L, open.knownCostMinor)
    }

    @Test fun grandfatheredCostOnlyUsesOwnerReviewedFmvGolden() {
        val cutoff = LocalDate.parse("2018-01-31")
        val acquired = LocalDate.parse("2018-01-30")
        fun cost(day: LocalDate, actual: Long, proceeds: Long,
            fmv: Long?, date: LocalDate? = cutoff) =
            NativeTaxLotTrace.reviewedGrandfatheredCostMinor(
                day, actual, proceeds, fmv, date)
        assertEquals(18_000L, cost(acquired, 10_000L, 20_000L, 18_000L))
        assertEquals(20_000L, cost(acquired, 10_000L, 20_000L, 25_000L))
        assertEquals(10_000L, cost(acquired, 10_000L, 20_000L, null))
        assertEquals(10_000L, cost(acquired, 10_000L, 20_000L, 18_000L, null))
        assertEquals(10_000L, cost(LocalDate.parse("2018-02-01"),
            10_000L, 20_000L, 18_000L))
        assertEquals(25_000L, cost(acquired, 25_000L, 20_000L, 18_000L))
        assertThrows(IllegalArgumentException::class.java) {
            cost(acquired, 10_000L, 20_000L, -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            cost(acquired, -1L, 20_000L, 18_000L)
        }
    }

    @Test fun originalCategoryDayThresholdsUseStrictGreaterThan() {
        fun longTerm(sold: String, category: String): Boolean {
            val events = listOf(
                event("open", "BUY", "2024-01-01", 100_000_000L),
                event("dispose", "SELL", sold, 100_000_000L, 11_000L))
            return NativeTaxLotTrace.trace(events, category,
                NativeTaxLotTrace.assessmentYear(LocalDate.parse(sold)))
                .items.single().ledgerLongTerm
        }
        // Frozen lots.ts longTermDays uses strict > 365 equity, >730 property.
        assertFalse(longTerm("2024-12-31", "EQUITY"))
        assertTrue(longTerm("2025-01-01", "EQUITY"))
        assertFalse(longTerm("2025-01-01", "REAL_ESTATE"))
        assertTrue(longTerm("2026-01-02", "REAL_ESTATE"))
    }

    @Test fun invalidAssessmentYearIsNeverGuessed() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTaxLotTrace.trace(emptyList(), "EQUITY", "AY28-2029")
        }
    }
}
