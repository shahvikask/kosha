package `in`.growurdhan.app.presentation

import `in`.growurdhan.app.presentation.screens.matchingRecordedHoldings
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.domain.finance.RecordedHoldingDashboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordedHoldingsSearchTest {
    private val holdings = listOf(
        RecordedHoldingDashboard("Synthetic Index Fund", "MUTUAL_FUND",
            "Primary Member", 100_000_000L, MoneyScaled(300_000), null, true, false),
        RecordedHoldingDashboard("Synthetic Housing Loan", "LIABILITY",
            "Secondary Member", 100_000_000L, MoneyScaled(125_000), null, false, true),
        RecordedHoldingDashboard("Gold Bond", "GOLD_SGB",
            null, 200_000_000L, MoneyScaled(25_000), null, true, false),
    )

    @Test fun blankSearchLeavesRecordedOrderAndValuesUnchanged() {
        assertEquals(holdings, matchingRecordedHoldings(holdings, "   "))
        assertEquals(holdings, matchingRecordedHoldings(holdings, ""))
    }

    @Test fun multiTermCaseInsensitiveSearchSupportsNameCategoryAndFamily() {
        assertEquals(listOf(holdings[0]), matchingRecordedHoldings(holdings, "index FUND"))
        assertEquals(listOf(holdings[1]), matchingRecordedHoldings(holdings, "liability"))
        assertEquals(listOf(holdings[2]), matchingRecordedHoldings(holdings, "gold sgb"))
        assertEquals(listOf(holdings[0]), matchingRecordedHoldings(holdings, "PRIMARY member"))
        assertEquals(listOf(holdings[1]), matchingRecordedHoldings(holdings, "secondary LOAN"))
    }

    @Test fun allTokensMustBelongToTheSameHolding() {
        assertTrue(matchingRecordedHoldings(holdings, "Index Housing").isEmpty())
        assertTrue(matchingRecordedHoldings(holdings, "account number").isEmpty())
    }
}
