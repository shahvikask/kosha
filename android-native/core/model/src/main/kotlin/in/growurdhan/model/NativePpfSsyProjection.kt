package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * P11 source-aligned *preview*, never a ledger event or a verified balance.
 *
 * Frozen src/lib/interest.ts: contributionThisFy / monthsHeldInFy / interestMinor.
 * Models ONE financial-year credit with separately attested opening balance,
 * annual contribution and contract/year-specific rate. We do not supply a
 * default or historic small-savings rate, compound the result into SQLCipher,
 * infer monthly-lowest balances, calculate taxes, or synthesize cash receipts.
 */
object NativePpfSsyProjection {
    enum class Scheme { PPF, SSY }
    data class Input(
        val scheme: Scheme,
        val openingBalanceMinor: Long,
        val annualRateBps: Int,
        val acquiredOn: LocalDate,
        val fyEnd: LocalDate,
        val annualContributionMinor: Long = 0L,
        val contributionOn: LocalDate? = null
    )
    data class Preview(
        val creditedMonths: Int,
        val contributionEarnsThisYear: Boolean,
        val eligibleInterestBaseMinor: Long,
        val projectedInterestMinor: Long,
        val projectedClosingBalanceMinor: Long
    )

    fun preview(input: Input): Preview {
        require(input.openingBalanceMinor >= 0 && input.annualContributionMinor >= 0) {
            "Negative attested principal or annual contribution"
        }
        require(input.annualRateBps in 1..10_000) {
            "Owner must confirm actual positive annual rate (basis points)"
        }
        require(input.fyEnd.month == java.time.Month.MARCH &&
            input.fyEnd.dayOfMonth == 31) { "Financial year must end 31 March" }
        val fyStart = input.fyEnd.minusYears(1).plusDays(1)
        require(!input.acquiredOn.isAfter(input.fyEnd)) {
            "Holding acquired after the financial year"
        }
        require(input.contributionOn == null || input.annualContributionMinor > 0L) {
            "Contribution date without an amount"
        }
        require(input.annualContributionMinor == 0L || input.contributionOn != null) {
            "Unverified contribution date"
        }
        val contribution = input.contributionOn
        if (contribution != null) require(contribution >= fyStart &&
            contribution <= input.fyEnd && contribution >= input.acquiredOn) {
            "Contribution must be recorded within the holding's financial year"
        }

        // Frozen monthsHeldInFy: full year if acquired before/at 1 April;
        // otherwise whole calendar months through 31 March (no day rounding).
        val from = if (input.acquiredOn > fyStart) input.acquiredOn else fyStart
        val months = if (from <= fyStart) 12
            else ChronoUnit.MONTHS.between(from, input.fyEnd).toInt().coerceIn(0, 12)
        val eligible = contribution != null && contribution.dayOfMonth <= 5
        val base = BigInteger.valueOf(input.openingBalanceMinor).add(
            if (eligible) BigInteger.valueOf(input.annualContributionMinor) else BigInteger.ZERO)
        // Original interestMinor: truncating integer division by 10000*12.
        val interest = base.multiply(BigInteger.valueOf(input.annualRateBps.toLong()))
            .multiply(BigInteger.valueOf(months.toLong()))
            .divide(BigInteger.valueOf(120_000L)).longValueExact()
        val closing = BigInteger.valueOf(input.openingBalanceMinor)
            .add(BigInteger.valueOf(input.annualContributionMinor))
            .add(BigInteger.valueOf(interest)).longValueExact()
        return Preview(months, eligible, base.longValueExact(), interest, closing)
    }
}
