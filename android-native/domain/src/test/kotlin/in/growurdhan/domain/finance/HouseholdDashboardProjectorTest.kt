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
        assertEquals(2, dashboard.unknownCostHoldings)
        assertEquals(80_000L, dashboard.byMember.single { it.ownerId == other }.liabilities.raw)
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
