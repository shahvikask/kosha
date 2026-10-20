package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeAnnualInterestCreditTest {
    private val fyEnd = LocalDate.parse("2026-03-31")
    private val start = LocalDate.parse("2024-01-01")
    private val today = LocalDate.parse("2026-10-20")
    private fun input(rate: Int = 710, principal: Long = 100_000_000L,
        kind: String = "PPF", acquired: LocalDate = start) =
        NativeAnnualInterestCredit.Input(kind, fyEnd, principal, rate, acquired)

    @Test fun exactWholeYearCreditUsesScaledIntegersNotFloatingPoint() {
        val credit = NativeAnnualInterestCredit.calculate(input(), today)
        assertEquals("2026-03-31", credit.date)
        assertEquals(7_100_000L, credit.amountMinor)
        assertEquals(107_100_000L, credit.closingMinor)
    }

    @Test fun ownerSpecifiedRateOverridesNoDefaultsAndMatchesSmallAmounts() {
        val credit = NativeAnnualInterestCredit.calculate(
            input(rate = 250, principal = 123_456L, kind = "EPF"), today)
        assertEquals(3_086L, credit.amountMinor) // floor 123456 * 250 / 10000
        assertEquals(126_542L, credit.closingMinor)
    }

    @Test fun futureFyOrWrongCalendarBoundaryIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeAnnualInterestCredit.calculate(input(), LocalDate.parse("2025-12-31"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAnnualInterestCredit.calculate(input().copy(
                financialYearEnd = LocalDate.parse("2026-04-01")), today)
        }
    }

    @Test fun partialYearCannotFakeDayFiveDepositsOrHistory() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeAnnualInterestCredit.calculate(input(
                acquired = LocalDate.parse("2025-05-06")), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAnnualInterestCredit.calculate(input(
                acquired = LocalDate.parse("2026-04-01")), today)
        }
    }

    @Test fun invalidRatePrincipalAndNonSchemeAreFailClosed() {
        for (rate in listOf(-1, 0, 10_001)) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeAnnualInterestCredit.calculate(input(rate = rate), today)
            }
        }
        for (invalid in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertThrows(Exception::class.java) {
                NativeAnnualInterestCredit.calculate(input(principal = invalid), today)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAnnualInterestCredit.calculate(input(kind = "UNVERIFIED"), today)
        }
    }
}
