package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

/** Source-attested src/lib/interest.ts periodInterest / compoundQuarterlyMinor / sgbPrincipalMinor. */
class NativeFdSgbProjectionTest {
    private fun fd(compounding: NativeFdSgbProjection.FdCompounding,
        balance: Long = 1_250_000L, deposited: Long? = 1_000_000L,
        rateBps: Int = 760, months: Int = 12
    ) = NativeFdSgbProjection.FdInput(balance, deposited, rateBps, compounding, months)

    @Test fun payoutPeriodsUseOriginalDepositedPrincipalNotAnInventedCurrentValue() {
        val monthly = NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.MONTHLY))
        assertEquals(NativeFdSgbProjection.FdMode.PAYOUT, monthly.mode)
        assertEquals(1_000_000L, monthly.principalMinor)
        assertEquals(12, monthly.paymentPeriodsPerYear)
        assertEquals(6_333L, monthly.projectedInterestMinor)
        val quarterly = NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.QUARTERLY))
        assertEquals(4, quarterly.paymentPeriodsPerYear)
        assertEquals(19_000L, quarterly.projectedInterestMinor)
        assertEquals(38_000L, NativeFdSgbProjection.fd(
            fd(NativeFdSgbProjection.FdCompounding.HALF_YEARLY)).projectedInterestMinor)
        assertEquals(76_000L, NativeFdSgbProjection.fd(
            fd(NativeFdSgbProjection.FdCompounding.YEARLY)).projectedInterestMinor)
    }

    @Test fun absentDepositUsesExplicitRecordedBalanceLikeFrozenFdFallback() {
        val p = NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.QUARTERLY,
            balance = 2_000_000L, deposited = null))
        assertEquals(2_000_000L, p.principalMinor)
        assertEquals(38_000L, p.projectedInterestMinor)
    }

    @Test fun cumulativeFdUsesOriginalMathPowAndOneFinalMathRound() {
        val full = NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.CUMULATIVE,
            balance = 1_000_000L, deposited = 9_000_000L))
        assertEquals(NativeFdSgbProjection.FdMode.COMPOUND, full.mode)
        assertEquals(1_000_000L, full.principalMinor)
        assertEquals(4, full.paymentPeriodsPerYear)
        assertEquals(78_194L, full.projectedInterestMinor)
        val quarter = NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.CUMULATIVE,
            balance = 1_000_000L, deposited = null, months = 3))
        assertEquals(19_000L, quarter.projectedInterestMinor)
    }

    @Test fun sgbCouponIsOnHistoricalIssuePriceNotCurrentMarketPrice() {
        val p = NativeFdSgbProjection.sgb(
            NativeFdSgbProjection.SgbInput(125_000_000L, 730_001L, 250))
        assertEquals(912_501L, p.originalIssuePrincipalMinor)
        assertEquals(11_406L, p.halfYearCouponMinor)
        val twoUnits = NativeFdSgbProjection.sgb(
            NativeFdSgbProjection.SgbInput(200_000_000L, 500_000L, 250))
        assertEquals(1_000_000L, twoUnits.originalIssuePrincipalMinor)
        assertEquals(12_500L, twoUnits.halfYearCouponMinor)
    }

    @Test fun noSilentUnknownRateNegativePrincipalOrInventedCoupon() {
        val badFd = listOf(
            fd(NativeFdSgbProjection.FdCompounding.MONTHLY, rateBps = 0),
            fd(NativeFdSgbProjection.FdCompounding.YEARLY, rateBps = 10_001),
            fd(NativeFdSgbProjection.FdCompounding.MONTHLY, balance = 0),
            fd(NativeFdSgbProjection.FdCompounding.MONTHLY, deposited = -1),
            fd(NativeFdSgbProjection.FdCompounding.CUMULATIVE, months = 13))
        badFd.forEach { assertThrows(IllegalArgumentException::class.java) {
            NativeFdSgbProjection.fd(it)
        } }
        listOf(0, -1, 10_001).forEach { badRate ->
            assertThrows(IllegalArgumentException::class.java) {
                NativeFdSgbProjection.sgb(NativeFdSgbProjection.SgbInput(
                    100_000_000L, 500_000L, badRate))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeFdSgbProjection.sgb(NativeFdSgbProjection.SgbInput(0, 500_000L, 250))
        }
    }

    @Test fun oversizedValuesFailClosedRatherThanOverstatingExactMoney() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeFdSgbProjection.fd(fd(NativeFdSgbProjection.FdCompounding.CUMULATIVE,
                balance = Long.MAX_VALUE))
        }
        assertThrows(ArithmeticException::class.java) {
            NativeFdSgbProjection.sgb(NativeFdSgbProjection.SgbInput(
                Long.MAX_VALUE, Long.MAX_VALUE, 250))
        }
    }
}
