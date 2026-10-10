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
        assertEquals(1, dashboard.unknownCostHoldings)
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

    @Test fun memberNamesAreMappedOnlyToMatchingOwners() {
        val report = PortfolioEngine.derive(
            emptyList(), emptyList(), LocalDate.parse("2026-10-09"),
            listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(
            report, mapOf(owner to "  Primary Member  ", OwnerId("stranger") to "Not enrolled"),
        )
        assertEquals("Primary Member", dashboard.byMember.single { it.ownerId == owner }.displayName)
        assertEquals(null, dashboard.byMember.single { it.ownerId == other }.displayName)
        assertEquals(2, dashboard.memberCount)
        assertEquals(0L, dashboard.netWorth.raw)
    }

    @Test fun debtOnlyHouseholdKeepsNegativeNetWorthAndNoAssetCategories() {
        val loan = FinancialHolding(
            HoldingId("loan-only"), owner, "Synthetic liability", "LIABILITY",
            MoneyScaled(480_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(loan), emptyList(), LocalDate.parse("2026-10-09"), listOf(owner),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(0L, dashboard.assets.raw)
        assertEquals(480_000L, dashboard.liabilities.raw)
        assertEquals(-480_000L, dashboard.netWorth.raw)
        assertTrue(dashboard.byCategory.isEmpty())
        assertEquals(1, dashboard.holdingCount)
        assertEquals(-480_000L, dashboard.byMember.single().netWorth.raw)
    }

    @Test fun memberAndCategoryTotalsReconcileForMixedPortfolio() {
        val holdings = listOf(
            FinancialHolding(HoldingId("cash"), owner, "Synthetic cash", "CASH",
                MoneyScaled(250_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("fund"), other, "Synthetic fund", "MUTUAL_FUND",
                MoneyScaled(175_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("loan"), owner, "Synthetic loan", "LIABILITY",
                MoneyScaled(125_000), QUANTITY_SCALE),
        )
        val report = PortfolioEngine.derive(
            holdings, emptyList(), LocalDate.parse("2026-10-09"), listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(dashboard.assets.raw, dashboard.byCategory.sumOf { it.value.raw })
        assertEquals(dashboard.assets.raw, dashboard.byMember.sumOf { it.assets.raw })
        assertEquals(dashboard.liabilities.raw, dashboard.byMember.sumOf { it.liabilities.raw })
        assertEquals(dashboard.netWorth.raw, dashboard.byMember.sumOf { it.netWorth.raw })
        assertEquals(2, dashboard.byCategory.size)
        assertEquals(2, dashboard.byCategory.sumOf { it.holdingCount })
        assertEquals(3, dashboard.holdingCount)
    }

    @Test fun recordedHoldingsExposeOnlyValuationFieldsAndNoLiabilityGains() {
        val equity = FinancialHolding(
            HoldingId("private-folio-id"), owner, "Synthetic Index Fund", "MUTUAL_FUND",
            MoneyScaled(230_000), QUANTITY_SCALE,
        )
        val debt = FinancialHolding(
            HoldingId("private-loan-id"), other, "Synthetic Housing Loan", "LIABILITY",
            MoneyScaled(125_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(equity, debt), emptyList(), LocalDate.parse("2026-10-09"),
            listOf(owner, other),
        )
        val dashboard = HouseholdDashboardProjector.from(
            report, mapOf(owner to "  Primary   Member  ", other to "Relative"),
        )
        assertEquals(2, dashboard.recordedHoldings.size)
        val asset = dashboard.recordedHoldings.first { !it.isLiability }
        assertEquals("Synthetic Index Fund", asset.name)
        assertEquals("Primary   Member", asset.ownerName)
        assertEquals(QUANTITY_SCALE, asset.quantityScaled)
        assertEquals(230_000L, asset.recordedValue.raw)
        assertTrue(asset.unknownHistoricalCost)
        assertEquals(null, asset.unrealisedGain)
        val loan = dashboard.recordedHoldings.single { it.isLiability }
        assertEquals(125_000L, loan.recordedValue.raw)
        assertEquals(null, loan.unrealisedGain)
        assertFalse(loan.unknownHistoricalCost)
        assertEquals(1, dashboard.unknownCostHoldings)
        assertFalse(dashboard.toString().contains("private-folio-id"))
        assertFalse(dashboard.toString().contains("private-loan-id"))
    }

    @Test fun recordedRowsReconcileExactlyWithHouseholdAssetsAndDebt() {
        val holdings = listOf(
            FinancialHolding(HoldingId("equity"), owner, "Index fund", "MUTUAL_FUND",
                MoneyScaled(125_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("deposit"), other, "Cash deposit", "CASH",
                MoneyScaled(215_000), QUANTITY_SCALE),
            FinancialHolding(HoldingId("debt"), owner, "Housing loan", "LIABILITY",
                MoneyScaled(110_000), QUANTITY_SCALE),
        )
        val dashboard = HouseholdDashboardProjector.from(PortfolioEngine.derive(
            holdings, emptyList(), LocalDate.parse("2026-10-09"), listOf(owner, other),
        ))
        assertEquals(dashboard.assets.raw,
            dashboard.recordedHoldings.filterNot { it.isLiability }.sumOf { it.recordedValue.raw })
        assertEquals(dashboard.liabilities.raw,
            dashboard.recordedHoldings.filter { it.isLiability }.sumOf { it.recordedValue.raw })
        assertEquals(dashboard.holdingCount, dashboard.recordedHoldings.size)
        assertEquals(2, dashboard.unknownCostHoldings)
    }

    @Test fun strictOwnerModeNeverInventsMemberFromAnOrphanHolding() {
        val orphan = FinancialHolding(
            HoldingId("secret-orphan-id"), OwnerId("unregistered"),
            "Synthetic unregistered holding", "EQUITY",
            MoneyScaled(999_000), QUANTITY_SCALE,
        )
        val report = PortfolioEngine.derive(
            listOf(orphan), emptyList(), LocalDate.parse("2026-10-09"),
            knownMembers = emptyList(), enforceKnownMembers = true,
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertEquals(0, dashboard.memberCount)
        assertEquals(0, dashboard.holdingCount)
        assertEquals(0L, dashboard.assets.raw)
        assertEquals(0L, dashboard.netWorth.raw)
        assertTrue(dashboard.recordedHoldings.isEmpty())
        assertFalse(dashboard.isReliable)
        assertTrue(dashboard.integrityWarnings.any { it.contains("Unknown family member") })
        assertFalse(dashboard.toString().contains("secret-orphan-id"))
    }

    @Test fun publicIntegrityWarningsNeverIncludePrivateEventOrHoldingIdentifiers() {
        val holding = FinancialHolding(
            HoldingId("sensitive-id"), owner, "Sensitive Sample Holding", "MUTUAL_FUND",
            MoneyScaled(50_000), 0L,
        )
        val orphanEvent = FinancialEvent(
            id = "sensitive-transaction-id",
            holdingId = HoldingId("missing-holding"),
            ownerId = owner,
            kind = FinancialEventKind.BUY,
            date = LocalDate.parse("2026-10-08"),
            quantityScaled = QUANTITY_SCALE,
            unitPrice = MoneyScaled(50_000),
        )
        val report = PortfolioEngine.derive(
            listOf(holding), listOf(orphanEvent), LocalDate.parse("2026-10-09"),
            knownMembers = listOf(owner), enforceKnownMembers = true,
        )
        val dashboard = HouseholdDashboardProjector.from(report)
        assertFalse(dashboard.isReliable)
        assertTrue(dashboard.integrityWarnings.any { it.contains("no registered holding") })
        assertFalse(dashboard.integrityWarnings.joinToString().contains("sensitive-transaction-id"))
        assertFalse(dashboard.integrityWarnings.joinToString().contains("sensitive-id"))
        assertFalse(dashboard.integrityWarnings.joinToString().contains("Sensitive Sample Holding"))
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
