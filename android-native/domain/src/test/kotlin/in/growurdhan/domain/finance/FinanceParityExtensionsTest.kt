package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic TypeScript parity vectors: emi.ts, interest.ts and lookthrough.ts. */
class FinanceParityExtensionsTest {
    private val owner = OwnerId("self")
    private val loan = HoldingId("loan")
    private fun d(text: String) = LocalDate.parse(text)
    private fun cash(rupees: Long) = MoneyScaled(Math.multiplyExact(rupees, 10_000))
    private fun plan(
        outstanding: Long = 9,
        emi: Long = 4,
        status: LoanEmiStatus = LoanEmiStatus.PAYING,
    ) = LoanEmiSchedule(
        loan, owner, d("2026-01-31"), 3, cash(outstanding), cash(emi), status,
    )

    @Test fun loanCalendarMonthClampsLikeJsInsteadOfReanchoring() {
        assertEquals(d("2026-02-28"), LoanEmiEngine.addCalendarMonths(d("2026-01-31"), 1))
        assertEquals(d("2026-03-28"), LoanEmiEngine.addCalendarMonths(d("2026-02-28"), 1))
        assertEquals(d("2024-02-29"), LoanEmiEngine.addCalendarMonths(d("2024-01-31"), 1))
    }

    @Test fun emiCatchupCapsFinalPaymentAndStopsWhenOutstandingHitsZero() {
        val reviewed = requireNotNull(LoanEmiEngine.plan(plan(), d("2026-04-30")))
        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-28")),
            reviewed.payments.map { it.date })
        assertEquals(listOf(cash(4), cash(4), cash(1)), reviewed.payments.map { it.amount })
        assertEquals(MoneyScaled(0), reviewed.outstanding)
        assertEquals(0, reviewed.remainingInstallments)
        assertEquals(LoanEmiStatus.STOPPED, reviewed.status)
        assertNull(reviewed.nextDue)
    }

    @Test fun pausedAndStoppedLoansNeverScheduleAutomaticRepayments() {
        assertNull(LoanEmiEngine.plan(plan(status = LoanEmiStatus.PAUSED), d("2026-04-30")))
        assertNull(LoanEmiEngine.plan(plan(status = LoanEmiStatus.STOPPED), d("2026-04-30")))
        assertNull(LoanEmiEngine.plan(plan(emi = 0), d("2026-04-30")))
    }

    @Test fun noFutureEmiAndAlreadyPostedDatesAreExcluded() {
        assertNull(LoanEmiEngine.plan(plan(), d("2025-12-31")))
        val reviewed = requireNotNull(LoanEmiEngine.plan(
            plan(outstanding = 100), d("2026-04-30"), setOf(d("2026-01-31")),
        ))
        assertEquals(listOf(d("2026-02-28"), d("2026-03-28")),
            reviewed.payments.map { it.date })
        assertEquals(1, reviewed.remainingInstallments)
        assertEquals(d("2026-04-28"), reviewed.nextDue)
        assertEquals(cash(92), reviewed.outstanding)
    }

    @Test fun emiResumeFindsNextFutureScheduleDate() {
        assertEquals(d("2026-04-28"),
            LoanEmiEngine.resumeDate(d("2026-01-31"), d("2026-04-01")))
        assertEquals(d("2026-06-01"), LoanEmiEngine.resumeDate(null, d("2026-06-01")))
    }

    @Test fun publishedSchemeRateTableDoesNotInventUnknownFdRate() {
        assertEquals(710, SavingsInterestEngine.defaultRateBps(SavingsKind.PPF))
        assertEquals(825, SavingsInterestEngine.defaultRateBps(SavingsKind.EPF))
        assertEquals(820, SavingsInterestEngine.defaultRateBps(SavingsKind.SSY))
        assertEquals(250, SavingsInterestEngine.defaultRateBps(SavingsKind.SGB))
        assertEquals(805, SavingsInterestEngine.defaultRateBps(SavingsKind.FRSB))
        assertNull(SavingsInterestEngine.defaultRateBps(SavingsKind.FD))
        assertNull(SavingsInterestEngine.defaultRateBps(SavingsKind.BOND))
    }

    @Test fun fixedIncomeCategoryDetectionMatchesLegacyReference() {
        assertEquals(SavingsKind.PPF, SavingsInterestEngine.kindForSubcategory("PPF - Public Provident"))
        assertEquals(SavingsKind.EPF, SavingsInterestEngine.kindForSubcategory("VPF account"))
        assertEquals(SavingsKind.SGB, SavingsInterestEngine.kindForSubcategory("Sovereign Gold Bond"))
        assertEquals(SavingsKind.FD, SavingsInterestEngine.kindForSubcategory("Bank FD"))
        assertEquals(SavingsKind.RD, SavingsInterestEngine.kindForSubcategory("Recurring Deposit"))
        assertNull(SavingsInterestEngine.kindForSubcategory("Equity Mutual Fund"))
    }

    @Test fun annualPpfAndEpfAccrualsMatchScaledMinorTruncation() {
        val principal = cash(100_000)
        assertEquals(cash(7_100), SavingsInterestEngine.annualAccrual(principal, 710, 12))
        assertEquals(cash(8_250), SavingsInterestEngine.annualAccrual(principal, 825, 12))
        assertEquals(MoneyScaled(0), SavingsInterestEngine.annualAccrual(principal, 710, 0))
        assertEquals(MoneyScaled(0), SavingsInterestEngine.annualAccrual(MoneyScaled(0), 710, 12))
    }

    @Test fun sgbHalfYearCouponIsPaidRatherThanCompounded() {
        val coupon = SavingsInterestEngine.coupon(cash(100_000), 250, 2)
        assertEquals(cash(1_250), coupon)
        val dates = SavingsInterestEngine.payoutDates(
            d("2025-08-31"), 6, d("2027-03-01"), d("2026-08-28"),
        )
        assertEquals(listOf(d("2026-02-28"), d("2026-08-28")), dates)
    }

    @Test fun financialYearBoundaryAndWholeMonthsAreDateSafe() {
        assertEquals(listOf(d("2025-03-31"), d("2026-03-31")),
            SavingsInterestEngine.fyEndDatesAfter(d("2025-01-01"), d("2026-04-01")))
        assertEquals(11, SavingsInterestEngine.monthsHeldInFy(
            d("2025-04-05"), d("2026-03-31")))
        assertEquals(12, SavingsInterestEngine.monthsHeldInFy(
            d("2025-04-01"), d("2026-03-31")))
        assertEquals(0, SavingsInterestEngine.wholeMonths(
            d("2026-07-31"), d("2026-05-01")))
    }

    @Test fun interestRejectsOverflowInsteadOfSilentlySaturatingMoney() {
        assertThrows(ArithmeticException::class.java) {
            SavingsInterestEngine.annualAccrual(MoneyScaled(Long.MAX_VALUE), 100_000, 12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavingsInterestEngine.coupon(cash(100), 710, 0)
        }
    }

    @Test fun aggressiveHybridSplitIsEstimatedAndMatchesReferenceSixtyFiveThirtyFive() {
        val split = ExposureSplitEngine.resolve(
            "Synthetic Aggressive Hybrid Fund", "MF", ExposureNode.EQUITY,
        )
        assertTrue(split.estimated)
        assertEquals(6500, split.slices.single { it.exposure == ExposureNode.EQUITY }.basisPoints)
        assertEquals(3500, split.slices.single { it.exposure == ExposureNode.DEBT }.basisPoints)
    }

    @Test fun mixedAssetExposuresAllocateExactRemainderWithoutDoubleCounting() {
        val split = ExposureSplitEngine.resolve(
            "Synthetic Multi Asset Allocation", "", ExposureNode.EQUITY,
        )
        assertTrue(split.estimated)
        val apportioned = ExposureSplitEngine.apportion(MoneyScaled(101), split)
        assertEquals(51L, apportioned.getValue(ExposureNode.EQUITY).raw)
        assertEquals(30L, apportioned.getValue(ExposureNode.DEBT).raw)
        assertEquals(20L, apportioned.getValue(ExposureNode.GOLD).raw)
        assertEquals(101L, apportioned.values.sumOf { it.raw })
    }

    @Test fun disclosedSplitsWinOverEstimatesAndRejectPercentMismatch() {
        val custom = listOf(
            ExposureSlice(ExposureNode.EQUITY, 7000),
            ExposureSlice(ExposureNode.DEBT, 3000),
        )
        val split = ExposureSplitEngine.resolve(
            "Conservative Hybrid", "MF", ExposureNode.EQUITY, custom,
        )
        assertFalse(split.estimated)
        assertEquals(custom, split.slices)
        assertThrows(IllegalArgumentException::class.java) {
            ExposureSplit(listOf(ExposureSlice(ExposureNode.GOLD, 9999)), false, "invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExposureSplit(listOf(ExposureSlice(ExposureNode.EQUITY, 5000),
                ExposureSlice(ExposureNode.EQUITY, 5000)), false, "duplicate")
        }
    }

    @Test fun unknownAssetUsesSingleExposureAndRejectsNegativeValues() {
        val split = ExposureSplitEngine.resolve("Other asset", "", ExposureNode.PROPERTY)
        assertEquals(false, split.estimated)
        assertEquals(mapOf(ExposureNode.PROPERTY to cash(20)),
            ExposureSplitEngine.apportion(cash(20), split))
        assertThrows(IllegalArgumentException::class.java) {
            ExposureSplitEngine.apportion(MoneyScaled(-1), split)
        }
    }

    @Test fun zeroUnitStatementCannotProposeFalseOpeningTrade() {
        val id = HoldingId("h1")
        val zero = StatementLine(id, owner, d("2026-10-08"), 0, cash(100))
        val holding = FinancialHolding(id, owner, "Synthetic", "MUTUAL_FUND", cash(100))
        val review = SnapshotReconciler.review(
            listOf(zero), listOf(holding), emptyList(), owner, "a".repeat(64),
        )
        assertEquals(ReconciliationOutcome.MATCH, review.lines.single().outcome)
        assertTrue(review.lines.single().proposedEvents.isEmpty())
    }
}
