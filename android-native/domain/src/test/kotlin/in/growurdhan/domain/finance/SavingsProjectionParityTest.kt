package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.MoneyScaled
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic financial inputs; estimated rates, never statutory/tax advice. */
class SavingsProjectionParityTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun amount(rupees: Long) = MoneyScaled(Math.multiplyExact(rupees, 10_000L))

    @Test fun quarterlyCompoundingExceedsSimpleAnnualInterest() {
        val compound = SavingsInterestEngine.quarterlyCompoundAccrual(
            amount(100_000), 710, 12,
        )
        assertTrue(compound.raw > amount(7_100).raw)
        assertTrue(compound.raw < amount(7_500).raw)
    }

    @Test fun zeroRateZeroPeriodAndZeroBalanceAccrueNothing() {
        assertEquals(MoneyScaled(0), SavingsInterestEngine.quarterlyCompoundAccrual(amount(10), 0, 12))
        assertEquals(MoneyScaled(0), SavingsInterestEngine.quarterlyCompoundAccrual(amount(10), 710, 0))
        assertEquals(MoneyScaled(0), SavingsInterestEngine.quarterlyCompoundAccrual(MoneyScaled(0), 710, 12))
    }

    @Test fun compoundingFromDepositProjectsMarchThirtyOneCredits() {
        val review = requireNotNull(SavingsInterestEngine.review(
            SavingsKind.PPF, SavingsInterestMode.COMPOUND,
            amount(100_000), 710, date("2024-04-01"), date("2026-04-01"),
        ))
        assertEquals(listOf(date("2025-03-31"), date("2026-03-31")),
            review.credits.map { it.postedOn })
        assertTrue(review.projectedBalance!!.raw > amount(114_200).raw)
        assertTrue(review.credits.all { it.estimated })
    }

    @Test fun manualVerifiedCompoundBalanceIsNeverSilentlyOverwritten() {
        assertNull(SavingsInterestEngine.review(
            SavingsKind.SSY, SavingsInterestMode.COMPOUND, amount(100_000),
            820, date("2025-01-01"), date("2026-10-08"),
            manuallyVerified = true,
        ))
    }

    @Test fun sgbHalfYearPayoutDoesNotChangeInvestmentValue() {
        val review = requireNotNull(SavingsInterestEngine.review(
            SavingsKind.SGB, SavingsInterestMode.PAYOUT, amount(100_000),
            250, date("2025-08-31"), date("2027-01-01"),
        ))
        assertEquals(listOf(date("2026-02-28"), date("2026-08-28")), review.credits.map { it.postedOn })
        assertTrue(review.credits.all { it.amount == amount(1_250) })
        assertNull(review.projectedBalance)
    }

    @Test fun maturityStopsLaterCouponsWithoutTrailingExtraEvents() {
        val review = requireNotNull(SavingsInterestEngine.review(
            SavingsKind.POMIS, SavingsInterestMode.PAYOUT, amount(100_000),
            740, date("2026-01-01"), date("2026-10-08"),
            maturityOn = date("2026-03-01"),
        ))
        assertEquals(listOf(date("2026-02-01"), date("2026-03-01")),
            review.credits.map { it.postedOn })
    }

    @Test fun duplicatePreviouslyPostedDatesAreNotReposted() {
        val review = requireNotNull(SavingsInterestEngine.review(
            SavingsKind.EPF, SavingsInterestMode.COMPOUND, amount(100_000),
            825, date("2024-04-01"), date("2026-10-08"),
            postedDates = setOf(date("2025-03-31")),
        ))
        assertEquals(listOf(date("2026-03-31")), review.credits.map { it.postedOn })
    }

    @Test fun sixMonthFrsbPayoutsUseCalendarOneJanuaryAndOneJuly() {
        val review = requireNotNull(SavingsInterestEngine.review(
            SavingsKind.FRSB, SavingsInterestMode.PAYOUT, amount(100_000),
            805, date("2025-10-08"), date("2026-10-08"),
        ))
        assertEquals(listOf(date("2026-01-01"), date("2026-07-01")),
            review.credits.map { it.postedOn })
    }

    @Test fun invalidRateOrPeriodIsRejectedFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SavingsInterestEngine.quarterlyCompoundAccrual(amount(100), -1, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavingsInterestEngine.review(
                SavingsKind.PPF, SavingsInterestMode.COMPOUND, amount(100),
                -1, date("2025-01-01"), date("2026-10-08"),
            )
        }
    }

    @Test fun firstInterestCannotOccurBeforeAcquisitionDate() {
        assertNull(SavingsInterestEngine.review(
            SavingsKind.SGB, SavingsInterestMode.PAYOUT, amount(100_000),
            250, date("2026-10-08"), date("2026-02-01"),
        ))
    }
}
