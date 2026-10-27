package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Frozen src/lib/interest.ts (contributionThisFy, monthsHeldInFy, interestMinor) goldens. */
class NativePpfSsyProjectionTest {
    private fun input(scheme: NativePpfSsyProjection.Scheme =
        NativePpfSsyProjection.Scheme.PPF,
        principal: Long = 1_000_000L, rateBps: Int = 710,
        acquired: String = "2024-01-01", end: String = "2026-03-31",
        contribution: Long = 1_000_000L, paid: String? = "2025-04-05"
    ) = NativePpfSsyProjection.Input(scheme, principal, rateBps,
        LocalDate.parse(acquired), LocalDate.parse(end), contribution,
        paid?.let(LocalDate::parse))

    @Test fun originalSourceContributionOnFifthEarnsThatFyUsingExactMinorUnits() {
        val p = NativePpfSsyProjection.preview(input())
        assertEquals(12, p.creditedMonths)
        assertTrue(p.contributionEarnsThisYear)
        assertEquals(2_000_000L, p.eligibleInterestBaseMinor)
        assertEquals(142_000L, p.projectedInterestMinor)
        assertEquals(2_142_000L, p.projectedClosingBalanceMinor)
    }

    @Test fun originalSourceContributionOnSixthDoesNotEarnThatYearButRemainsInClosing() {
        val p = NativePpfSsyProjection.preview(input(paid = "2025-04-06"))
        assertEquals(12, p.creditedMonths)
        assertFalse(p.contributionEarnsThisYear)
        assertEquals(1_000_000L, p.eligibleInterestBaseMinor)
        assertEquals(71_000L, p.projectedInterestMinor)
        assertEquals(2_071_000L, p.projectedClosingBalanceMinor)
    }

    @Test fun sourceSsyPartialYearUsesWholeMonthsAndDistinctAttestedRate() {
        val p = NativePpfSsyProjection.preview(input(
            scheme = NativePpfSsyProjection.Scheme.SSY, rateBps = 820,
            acquired = "2025-10-01", paid = "2025-10-05"))
        assertEquals(5, p.creditedMonths)
        assertTrue(p.contributionEarnsThisYear)
        // floor(2_000_000 * 820 * 5 / 120_000)
        assertEquals(68_333L, p.projectedInterestMinor)
        assertEquals(2_068_333L, p.projectedClosingBalanceMinor)
    }

    @Test fun sourceFinancialYearBoundaryAndFebLeapDateDoNotRoundPartialMonthsUp() {
        val p = NativePpfSsyProjection.preview(input(
            principal = 120_000L, rateBps = 800,
            acquired = "2024-02-29", end = "2024-03-31",
            contribution = 0L, paid = null))
        assertEquals(1, p.creditedMonths)
        assertEquals(800L, p.projectedInterestMinor)
        assertEquals(120_800L, p.projectedClosingBalanceMinor)
        val full = NativePpfSsyProjection.preview(input(
            principal = 120_000L, rateBps = 800,
            acquired = "2025-04-01", contribution = 0L, paid = null))
        assertEquals(12, full.creditedMonths)
        assertEquals(9_600L, full.projectedInterestMinor)
    }

    @Test fun noGuessOfRateOrDateAndNoOutOfYearContribution() {
        val bad = listOf(
            input(rateBps = 0),
            input(rateBps = 10_001),
            input(principal = -1),
            input(contribution = -1),
            input(contribution = 1_000L, paid = null),
            input(contribution = 0L, paid = "2025-04-05"),
            input(paid = "2024-04-05"),
            input(paid = "2026-04-05"),
            input(acquired = "2026-04-01"),
            input(end = "2026-03-30"),
            input(acquired = "2025-10-10", paid = "2025-10-05"))
        bad.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                NativePpfSsyProjection.preview(value)
            }
        }
    }

    @Test fun largeAttestedValuesFailClosedOnOverflowWithoutFloatingPointFallback() {
        assertThrows(ArithmeticException::class.java) {
            NativePpfSsyProjection.preview(input(
                principal = Long.MAX_VALUE, rateBps = 10_000,
                contribution = 1L, paid = "2025-04-05"))
        }
    }
}
