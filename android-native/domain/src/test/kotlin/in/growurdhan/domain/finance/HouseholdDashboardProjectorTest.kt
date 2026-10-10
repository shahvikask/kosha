package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HouseholdDashboardProjectorTest {
    private val owner = OwnerId("primary")
    private val other = OwnerId("family")

    @Test fun emptyFamilyMemberIsIncludedWithoutInventingHoldings() {
        val report = PortfolioEngine.derive(
            holdings = emptyList(),
            events = emptyList(),
            asOf = LocalDate.parse("2026-10-09"),
            knownMembers = listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(2, dashboard.memberCount)
        assertEquals(0, dashboard.holdingCount)
        assertEquals(0L, dashboard.netWorth.raw)
        assertEquals(0, dashboard.unknownCostHoldings)
        assertTrue(dashboard.isReliable)
    }

    @Test fun unknownHistoricalCostIsNeverPresentedAsKnownGain() {
        val holding = FinancialHolding(
            HoldingId("h1"), owner, "Imported CAS position", "MUTUAL_FUND",
            MoneyScaled(150_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(holding), emptyList(), LocalDate.parse("2026-10-09"),
            listOf(owner),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(1, dashboard.holdingCount)
        assertEquals(1, dashboard.unknownCostHoldings)
        assertEquals(150_000L, dashboard.assets.raw)
        assertEquals(dashboard.assets, dashboard.netWorth)
        assertEquals(null, report.members.single().holdings.single().unrealisedGain)
    }

    @Test fun liabilitiesReduceFamilyNetWorthAndStayAttributedToOwner() {
        val assets = FinancialHolding(
            HoldingId("asset"), owner, "Bank balance", "FIXED_INCOME",
            MoneyScaled(250_000), QUANTITY_SCALE,
        )
        val debt = FinancialHolding(
            HoldingId("debt"), other, "Loan", "LIABILITY",
            MoneyScaled(80_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(assets, debt), emptyList(), LocalDate.parse("2026-10-09"),
            listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(250_000L, dashboard.assets.raw)
        assertEquals(80_000L, dashboard.liabilities.raw)
        assertEquals(170_000L, dashboard.netWorth.raw)
        assertEquals(2, dashboard.holdingCount)
        assertEquals(1, dashboard.byCategory.size)
        assertEquals("FIXED_INCOME", dashboard.byCategory.single().category)
        assertEquals(250_000L, dashboard.byCategory.single().value.raw)
        assertEquals(2, dashboard.unknownCostHoldings)
        assertEquals(80_000L, dashboard.byMember.single { it.ownerId == other }.liabilities.raw)
    }

    @Test fun categoryBreakdownAggregatesAcrossFamilyAndSortsDeterministically() {
        val holdings = listOf(
            FinancialHolding(HoldingId("gold"), owner, "Gold", "GOLD", MoneyScaled(50_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("mf1"), owner, "Fund A", "MUTUAL_FUND", MoneyScaled(120_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("mf2"), other, "Fund B", "MUTUAL_FUND", MoneyScaled(80_000), QUANTITY_SCALE),
        )
        val report = PortfolioEngine.derive(
            holdings, emptyList(), LocalDate.parse("2026-10-09"), listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(listOf("GOLD", "MUTUAL_FUND"), dashboard.byCategory.map { it.category })
        assertEquals(listOf(50_000L, 200_000L), dashboard.byCategory.map { it.value.raw })
        assertEquals(listOf(1, 2), dashboard.byCategory.map { it.holdingCount })
        assertEquals(dashboard.assets.raw, dashboard.byCategory.sumOf { it.value.raw })
    }

    @Test fun memberBreakdownReconcilesToHouseholdTotals() {
        val holdings = listOf(
            FinancialHolding(HoldingId("member1"), owner, "Savings", "CASH", MoneyScaled(300_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("member2"), other, "Deposit", "FIXED_INCOME", MoneyScaled(125_000), QUANTITY_SCALE),
        )
        val report = PortfolioEngine.derive(
            holdings, emptyList(), LocalDate.parse("2026-10-09"), listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(2, dashboard.byMember.size)
        assertEquals(425_000L, dashboard.netWorth.raw)
        assertEquals(dashboard.netWorth.raw, dashboard.byMember.sumOf { it.netWorth.raw })
        assertEquals(dashboard.assets.raw, dashboard.byMember.sumOf { it.assets.raw })
        assertEquals(dashboard.liabilities.raw, dashboard.byMember.sumOf { it.liabilities.raw })
        assertEquals(1, dashboard.byMember.single { it.ownerId == owner }.holdings)
        assertEquals(1, dashboard.byMember.single { it.ownerId == other }.holdings)
    }

    @Test fun integrityProblemsAreNotHiddenFromDashboard() {
        val holding = FinancialHolding(
            HoldingId("h2"), OwnerId("unrecognized"), "Unknown owner", "EQUITY",
            MoneyScaled(200_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(holding), emptyList(), LocalDate.parse("2026-10-09"),
            listOf(owner),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertFalse(dashboard.isReliable)
        assertTrue(dashboard.integrityWarnings.any { it.contains("Unknown family member") })
        assertEquals(0L, dashboard.assets.raw)
        assertEquals(0, dashboard.holdingCount)
    }
}
