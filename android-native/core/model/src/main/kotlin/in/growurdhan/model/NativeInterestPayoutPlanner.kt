package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate

/**
 * Narrow, owner-reviewed interest P22 source path.
 *
 * Supports only fixed-frequency cash coupons from original interest.ts:
 * SGB half-year, SCSS quarterly, POMIS monthly. No default historical
 * scheme rates, no guessed purchase principal, no compounding, no EPF/PPF
 * monthly-minimum proxy, and no money added to the holding balance.
 *
 * The owner MUST supply the principal and annual basis-point rate displayed
 * on their actual contract. Every due date/amount is shown before approval.
 */
object NativeInterestPayoutPlanner {
    enum class Kind(val stepMonths: Int, val periodsPerYear: Int) {
        SGB(6, 2), SCSS(3, 4), POMIS(1, 12)
    }
    data class Input(
        val kind: Kind,
        val principalMinor: Long,
        val annualRateBps: Int,
        val acquiredOn: LocalDate,
        val asOf: LocalDate,
        val maturityOn: LocalDate? = null
    )
    data class Coupon(val date: LocalDate, val amountMinor: Long)
    data class Plan(val coupons: List<Coupon>, val alreadyRecorded: Int)

    fun plan(input: Input, postedDates: Set<LocalDate> = emptySet()): Plan {
        require(input.principalMinor > 0L && input.annualRateBps in 1..10_000) {
            "Review actual nonzero principal and annual interest rate"
        }
        require(!input.acquiredOn.isAfter(input.asOf) &&
            (input.maturityOn == null || !input.maturityOn.isBefore(input.acquiredOn))) {
            "Invalid contract or payout date"
        }
        require(input.asOf.isBefore(input.acquiredOn.plusYears(41))) {
            "Historical payout requires a bounded reviewed contract"
        }
        val amount = BigInteger.valueOf(input.principalMinor)
            .multiply(BigInteger.valueOf(input.annualRateBps.toLong()))
            .divide(BigInteger.valueOf(10_000L * input.kind.periodsPerYear))
            .longValueExact()
        require(amount > 0L) { "Interest would round to zero at the stored scale" }
        val output = mutableListOf<Coupon>()
        var posted = 0
        for (period in 1..480) {
            // Re-anchor every period to the original purchase date: using the
            // previous due date repeatedly permanently drifts 31 January.
            val day = input.acquiredOn.plusMonths(period.toLong() * input.kind.stepMonths)
            if (day.isAfter(input.asOf) ||
                (input.maturityOn != null && day.isAfter(input.maturityOn))) break
            if (day in postedDates) posted++ else output += Coupon(day, amount)
            require(output.size <= 240) { "Review shorter coupon window first" }
        }
        return Plan(output, posted)
    }
}
