package `in`.growurdhan.model

import java.math.BigInteger
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * P11 non-posting golden for the frozen src/lib/interest.ts contract.
 * Does NOT imply current FD rates, SGB purchase evidence or received cash.
 * Every contractual rate and balance must come from an owner-reviewed record.
 */
object NativeFdSgbProjection {
    private const val QUANTITY_SCALE = 100_000_000L
    private const val JS_MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    enum class FdCompounding { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY, CUMULATIVE }
    enum class FdMode { PAYOUT, COMPOUND }
    data class FdInput(
        val recordedBalanceMinor: Long,
        val recordedDepositMinor: Long?,
        val annualRateBps: Int,
        val compounding: FdCompounding,
        val monthsHeldInFy: Int
    )
    data class FdPreview(
        val mode: FdMode,
        val principalMinor: Long,
        val paymentPeriodsPerYear: Int,
        val projectedInterestMinor: Long
    )
    data class SgbInput(
        val quantityScaled: Long,
        val originalIssuePriceMinor: Long,
        val contractualCouponRateBps: Int
    )
    data class SgbPreview(val originalIssuePrincipalMinor: Long,
        val halfYearCouponMinor: Long)

    fun fd(input: FdInput): FdPreview {
        require(input.annualRateBps in 1..10_000) { "Owner-attested FD annual rate required" }
        require(input.recordedBalanceMinor > 0) { "Recorded FD balance required" }
        require(input.recordedDepositMinor == null || input.recordedDepositMinor >= 0) {
            "Invalid historical FD deposit"
        }
        require(input.monthsHeldInFy in 1..12) { "Only one source financial year may be previewed" }
        val periods = when (input.compounding) {
            FdCompounding.MONTHLY -> 12
            FdCompounding.QUARTERLY, FdCompounding.CUMULATIVE -> 4
            FdCompounding.HALF_YEARLY -> 2
            FdCompounding.YEARLY -> 1
        }
        val cumulative = input.compounding == FdCompounding.CUMULATIVE
        // Frozen interestSpecOf: payout FDs use deposited basis where actually
        // recorded; cumulative FDs use the recorded balance as their base.
        val principal = if (!cumulative && (input.recordedDepositMinor ?: 0L) > 0L)
            input.recordedDepositMinor!! else input.recordedBalanceMinor
        val interest = if (!cumulative) {
            BigInteger.valueOf(principal).multiply(BigInteger.valueOf(input.annualRateBps.toLong()))
                .divide(BigInteger.valueOf(10_000L * periods)).longValueExact()
        } else {
            // Frozen JS uses Math.pow and Math.round(Number(principal)*factor),
            // not per-quarter integer rounding. Fail closed on unsafe JS-number
            // magnitudes instead of silently claiming exact monetary parity.
            require(principal <= JS_MAX_SAFE_INTEGER) { "Principal exceeds legacy safe integer" }
            val annualPercent = input.annualRateBps / 100.0
            val factor = (1 + annualPercent / 100 / 4).pow(input.monthsHeldInFy / 3.0)
            val next = principal.toDouble() * factor
            require(next.isFinite() && next <= JS_MAX_SAFE_INTEGER) {
                "Projected amount exceeds legacy safe integer"
            }
            (next.roundToLong() - principal).coerceAtLeast(0)
        }
        return FdPreview(if (cumulative) FdMode.COMPOUND else FdMode.PAYOUT,
            principal, periods, interest)
    }

    fun sgb(input: SgbInput): SgbPreview {
        require(input.quantityScaled > 0 && input.originalIssuePriceMinor > 0) {
            "SGB issue-price evidence and actual quantity required"
        }
        require(input.contractualCouponRateBps in 1..10_000) {
            "Owner-attested SGB contractual coupon rate required"
        }
        // Frozen sgbPrincipalMinor = floor(qtyScaled * issue / 1e8);
        // periodInterest = floor(principal * rateBps / (10000 * 2)).
        val principal = BigInteger.valueOf(input.quantityScaled)
            .multiply(BigInteger.valueOf(input.originalIssuePriceMinor))
            .divide(BigInteger.valueOf(QUANTITY_SCALE)).longValueExact()
        require(principal > 0) { "SGB issue principal is zero at recorded precision" }
        val coupon = BigInteger.valueOf(principal)
            .multiply(BigInteger.valueOf(input.contractualCouponRateBps.toLong()))
            .divide(BigInteger.valueOf(20_000L)).longValueExact()
        return SgbPreview(principal, coupon)
    }
}
