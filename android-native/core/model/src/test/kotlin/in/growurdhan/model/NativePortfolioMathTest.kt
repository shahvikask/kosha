package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativePortfolioMathTest {
    private fun event(id: String, kind: String, day: String, qty: Long,
        price: Long = 10_000L, knownCost: Boolean = true, charges: Long = 0L,
        supersedes: String? = null) = NativePortfolioMath.Event(
        id, "family-1", "holding-1", kind, day, qty, price, 0L, charges, supersedes, knownCost)

    @Test fun frozenEightCategoriesAndAllSubcategoriesRemainPresent() {
        assertEquals(8, NativeAssetTaxonomy.subcategories.size)
        assertTrue("Sovereign Gold Bond" in NativeAssetTaxonomy.subcategories.getValue("GOLD_SGB"))
        assertTrue("POMIS" in NativeAssetTaxonomy.subcategories.getValue("FIXED_INCOME"))
        assertTrue("AIF" in NativeAssetTaxonomy.subcategories.getValue("SPECIALISED"))
    }

    @Test fun fifoUsesScaledIntegerCostAndAllocatedSaleCharges() {
        val events = listOf(event("buy", "BUY", "2024-01-01", 200_000_000, 20_000, charges = 100),
            event("sell", "SELL", "2025-08-01", 100_000_000, 30_000, charges = 50))
        val result = NativePortfolioMath.fold(events, "EQUITY")
        assertEquals(100_000_000L, result.quantityScaled)
        assertEquals(20_050L, result.knownCostMinor)
        assertEquals(1, result.disposals.size)
        assertEquals(20_050L, result.disposals.single().costMinor)
        assertEquals(9_900L, result.disposals.single().gainMinor)
        assertTrue(result.disposals.single().longTerm)
    }

    @Test fun unknownCostCannotBeInventedFromZero() {
        val result = NativePortfolioMath.fold(
            listOf(event("b", "OPENING_BALANCE", "2024-03-31", 100_000_000, knownCost = false),
                event("s", "SELL", "2025-04-01", 20_000_000)),
            "MUTUAL_FUND")
        assertNull(result.disposals.single().costMinor)
        assertNull(result.disposals.single().gainMinor)
        assertTrue(result.hasUnknownCost)
    }

    @Test fun snapshotReductionsCannotBecomeTaxableDisposals() {
        val result = NativePortfolioMath.fold(
            listOf(event("o", "BUY", "2024-01-01", 100_000_000),
                event("snap", "SNAPSHOT_ADJUSTMENT", "2024-08-01", -40_000_000)),
            "EQUITY")
        assertTrue(result.errors.isEmpty())
        assertTrue(result.disposals.isEmpty())
        assertEquals(60_000_000L, result.quantityScaled)
    }

    @Test fun splitKeepsTotalBasisWithoutFloatingPoint() {
        val result = NativePortfolioMath.fold(listOf(
            event("o", "BUY", "2024-01-01", 100_000_000, 24_000),
            event("sp", "SPLIT", "2024-04-01", 2, 1)), "EQUITY")
        assertEquals(200_000_000L, result.quantityScaled)
        assertEquals(24_000L, result.knownCostMinor)
    }

    @Test fun correctionsSupersedeAndCannotCrossOwnershipOrCycle() {
        val events = listOf(event("b", "BUY", "2024-01-01", 100_000_000),
            event("correct", "CORRECTION", "2024-01-02", 200_000_000, supersedes = "b"))
        assertEquals(200_000_000L, NativePortfolioMath.fold(events, "EQUITY").quantityScaled)
        try {
            NativePortfolioMath.fold(events + event("cycle", "CORRECTION", "2024-01-01",
                1L, supersedes = "cycle"), "EQUITY")
            fail("Self-correction must not be accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun ownerIsolationRejectsMixedEvents() {
        val foreign = event("foreign", "BUY", "2024-01-01", 1).copy(ownerId = "someone-else")
        try {
            NativePortfolioMath.fold(listOf(event("self", "BUY", "2024-01-01", 1), foreign), "EQUITY")
            fail("Cross-owner fold must not be allowed")
        } catch (_: IllegalArgumentException) { }
    }
}
