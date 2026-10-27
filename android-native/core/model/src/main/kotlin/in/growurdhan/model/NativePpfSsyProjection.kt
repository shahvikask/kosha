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

    /** Year-by-year owner-attested scenario, not a statement-backed passbook. */
    data class YearPreview(val financialYearEnd: LocalDate, val rateBps: Int,
        val annualContributionMinor: Long, val result: Preview)
    data class HistoryPreview(val years: List<YearPreview>,
        val projectedInterestMinor: Long, val projectedClosingMinor: Long)

    /**
     * Each FY has an explicit opening balance, rate and contribution date.
     * Never silently apply today's scheme rate or repeated annual contribution
     * to an older year, or treat the source's day-five simplification as real
     * monthly-minimum bank-passbook interest. A missing FY stops the scenario.
     * The caller must separately attest the very first opening balance.
     */
    fun previewHistory(years: List<Input>, asOf: LocalDate): HistoryPreview {
        require(years.isNotEmpty() && years.size <= 80) {
            "Explicit per-year inputs required"
        }
        val first = years.first()
        var preceding: Input? = null
        var priorClosing: Long? = null
        val results = mutableListOf<YearPreview>()
        var projectedInterest = 0L
        for (input in years) {
            require(input.scheme == first.scheme && input.acquiredOn == first.acquiredOn) {
                "Mixed scheme or acquisition provenance in one scenario"
            }
            require(!input.fyEnd.isAfter(asOf)) { "Future FY cannot be projected as earned" }
            val previous = preceding
            if (previous != null) {
                require(input.fyEnd == previous.fyEnd.plusYears(1)) {
                    "A missing financial year requires its own attested rate and deposits"
                }
                require(input.openingBalanceMinor == priorClosing) {
                    "Unreconciled opening amount; never bridge an unknown cash flow"
                }
            }
            val result = preview(input)
            results += YearPreview(input.fyEnd, input.annualRateBps,
                input.annualContributionMinor, result)
            projectedInterest = Math.addExact(projectedInterest, result.projectedInterestMinor)
            priorClosing = result.projectedClosingBalanceMinor
            preceding = input
        }
        return HistoryPreview(results, projectedInterest, requireNotNull(priorClosing))
    }
}
