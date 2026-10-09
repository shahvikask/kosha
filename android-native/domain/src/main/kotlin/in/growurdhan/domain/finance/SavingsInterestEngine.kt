package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.MoneyScaled
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import java.time.LocalDate

/**
 * Reference inputs: src/lib/interest.ts, Jul–Sep 2026 small-savings rates
 * and FY 2025-26 EPF rate. Not a rate history or authoritative tax service.
 * Money and quantities remain scaled integers; no floating-point balances.
 */
enum class SavingsKind { SGB, PPF, EPF, SSY, FD, SCSS, POMIS, NSC, KVP, RD, FRSB, BOND, OTHER }
/**
 * These are financial projections, NOT automatically posted ledger events. A
 * mutable amount entered by the user must be verified before catch-up is shown.
 */
enum class SavingsInterestMode { COMPOUND, PAYOUT }
data class SavingsInterestCredit(
    val postedOn: LocalDate,
    val amount: MoneyScaled,
    val estimated: Boolean,
)
data class SavingsInterestReview(
    val credits: List<SavingsInterestCredit>,
    /** Present only for products where interest compounds into the holding. */
    val projectedBalance: MoneyScaled?,
    val assumption: String,
)

object SavingsInterestEngine {
    private val publishedBps = mapOf(
        SavingsKind.SGB to 250, SavingsKind.PPF to 710, SavingsKind.EPF to 825,
        SavingsKind.SSY to 820, SavingsKind.SCSS to 820, SavingsKind.POMIS to 740,
        SavingsKind.NSC to 770, SavingsKind.KVP to 750, SavingsKind.RD to 670,
        SavingsKind.FRSB to 805,
    )
    fun defaultRateBps(kind: SavingsKind): Int? = publishedBps[kind]

    fun kindForSubcategory(subcategory: String): SavingsKind? {
        val sub = subcategory.lowercase()
        fun has(word: String) = Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(sub)
        return when {
            "sovereign gold" in sub || has("sgb") -> SavingsKind.SGB
            has("ppf") || "public provident" in sub -> SavingsKind.PPF
            has("epf") || has("vpf") || "provident fund" in sub -> SavingsKind.EPF
            "sukanya" in sub || has("ssy") -> SavingsKind.SSY
            has("fd") || "fixed deposit" in sub || "term deposit" in sub -> SavingsKind.FD
            has("scss") || "senior citizen savings" in sub -> SavingsKind.SCSS
            "pomis" in sub || "monthly income" in sub -> SavingsKind.POMIS
            has("nsc") || "national savings certificate" in sub -> SavingsKind.NSC
            has("kvp") || "kisan vikas" in sub -> SavingsKind.KVP
            has("rd") || "recurring deposit" in sub -> SavingsKind.RD
            "floating rate" in sub || "frsb" in sub -> SavingsKind.FRSB
            "bond" in sub || "goi" in sub -> SavingsKind.BOND
            else -> null
        }
    }

    fun wholeMonths(from: LocalDate, to: LocalDate): Int {
        if (to < from) return 0
        var months = (to.year - from.year) * 12 + to.monthValue - from.monthValue
        if (to.dayOfMonth < from.dayOfMonth) months--
        return months.coerceAtLeast(0)
    }

    fun fyEndDatesAfter(after: LocalDate, asOf: LocalDate): List<LocalDate> {
        if (after >= asOf) return emptyList()
        var year = after.year
        if (after.monthValue > 3 || after == LocalDate.of(after.year, 3, 31)) year++
        val dates = mutableListOf<LocalDate>()
        while (year <= asOf.year && dates.size <= 80) {
            val end = LocalDate.of(year, 3, 31)
            if (end > after && end <= asOf) dates += end
            year++
        }
        return dates
    }

    fun monthsHeldInFy(acquired: LocalDate?, fyEnd: LocalDate): Int {
        require(fyEnd.monthValue == 3 && fyEnd.dayOfMonth == 31)
        val start = LocalDate.of(fyEnd.year - 1, 4, 1)
        val from = if (acquired != null && acquired > start) acquired else start
        if (from > fyEnd) return 0
        return if (from <= start) 12 else wholeMonths(from, fyEnd).coerceAtMost(12)
    }

    /** TS interestMinor truncates the scaled result, never rounds upward. */
    fun annualAccrual(principal: MoneyScaled, rateBps: Int, months: Int): MoneyScaled {
        require(rateBps in 0..100_000 && months in 0..12)
        if (principal.raw <= 0 || rateBps == 0 || months == 0) return MoneyScaled(0)
        return MoneyScaled((
            BigInteger.valueOf(principal.raw) * BigInteger.valueOf(rateBps.toLong()) *
                BigInteger.valueOf(months.toLong()) / BigInteger.valueOf(120_000L)
            ).toCheckedLong())
    }

    /** TS periodInterest; a payout is not compounded into the investment. */
    fun coupon(principal: MoneyScaled, rateBps: Int, payoutsPerYear: Int): MoneyScaled {
        require(rateBps in 0..100_000 && payoutsPerYear in 1..12)
        if (principal.raw <= 0 || rateBps == 0) return MoneyScaled(0)
        return MoneyScaled((
            BigInteger.valueOf(principal.raw) * BigInteger.valueOf(rateBps.toLong()) /
                BigInteger.valueOf(10_000L * payoutsPerYear)
            ).toCheckedLong())
    }

    /** TS steppedDates: iterative month clamping with an optional maturity cap. */
    fun payoutDates(
        acquired: LocalDate, stepMonths: Int, asOf: LocalDate, maturity: LocalDate? = null,
    ): List<LocalDate> {
        require(stepMonths in 1..12)
        val dates = mutableListOf<LocalDate>()
        var cursor = LoanEmiEngine.addCalendarMonths(acquired, stepMonths.toLong())
        repeat(480) {
            if (cursor > asOf || (maturity != null && cursor > maturity)) return dates
            dates += cursor
            if (maturity != null && cursor == maturity) return dates
            cursor = LoanEmiEngine.addCalendarMonths(cursor, stepMonths.toLong())
        }
        return dates
    }

    /**
     * Matches src/lib/interest.ts compoundQuarterlyMinor for a fixed
     * principal and a single FY interval: one final rounding, not a
     * rounded payment every quarter. Rate is still an assumption.
     */
    fun quarterlyCompoundAccrual(
        principal: MoneyScaled, rateBps: Int, months: Int,
    ): MoneyScaled {
        require(rateBps in 0..100_000 && months in 0..12)
        if (principal.raw <= 0 || rateBps == 0 || months == 0) return MoneyScaled(0)
        val factor = (1.0 + rateBps / 40_000.0).pow(months / 3.0)
        require(factor.isFinite() && factor > 0.0)
        val after = BigDecimal.valueOf(principal.raw).multiply(BigDecimal.valueOf(factor))
            .setScale(0, RoundingMode.HALF_UP)
            .toBigIntegerExact().toCheckedLong()
        return MoneyScaled(maxOf(0L, Math.subtractExact(after, principal.raw)))
    }

    /**
     * Review catch-up for income-paying and balance-compounding products. It
     * honors reported maturity and posted dates, but cannot model PPF/SSY
     * daily balances without the actual contribution transaction history.
     * If original/accrued balance is a verified manual input, never overwrite.
     */
    fun review(
        kind: SavingsKind,
        mode: SavingsInterestMode,
        startingBalance: MoneyScaled,
        rateBps: Int,
        acquiredOn: LocalDate,
        asOf: LocalDate,
        maturityOn: LocalDate? = null,
        postedDates: Set<LocalDate> = emptySet(),
        manuallyVerified: Boolean = false,
    ): SavingsInterestReview? {
        require(rateBps in 0..100_000)
        if (startingBalance.raw <= 0 || rateBps <= 0 || acquiredOn > asOf) return null
        if (mode == SavingsInterestMode.COMPOUND && manuallyVerified) return null
        val horizon = if (maturityOn != null && maturityOn < asOf) maturityOn else asOf
        if (horizon < acquiredOn) return null
        val dates: List<LocalDate> = when (mode) {
            SavingsInterestMode.COMPOUND ->
                fyEndDatesAfter(acquiredOn, horizon)
            SavingsInterestMode.PAYOUT ->
                if (kind == SavingsKind.FRSB) {
                    buildList {
                        for (year in acquiredOn.year..horizon.year) {
                            for (month in listOf(1, 7)) {
                                val day = LocalDate.of(year, month, 1)
                                if (day > acquiredOn && day <= horizon) add(day)
                            }
                        }
                    }
                } else {
                    val interval = when (kind) {
                        SavingsKind.POMIS -> 1
                        SavingsKind.SCSS -> 3
                        SavingsKind.FD -> 3
                        SavingsKind.SGB, SavingsKind.FRSB, SavingsKind.BOND -> 6
                        else -> 12
                    }
                    payoutDates(acquiredOn, interval, horizon, maturityOn)
                }
        }
        if (dates.isEmpty()) return null
        var projected = startingBalance.raw
        val results = mutableListOf<SavingsInterestCredit>()
        for (date in dates) {
            val accrual = if (mode == SavingsInterestMode.PAYOUT) {
                val frequency = when (kind) {
                    SavingsKind.POMIS -> 12
                    SavingsKind.SCSS, SavingsKind.FD -> 4
                    SavingsKind.SGB, SavingsKind.FRSB, SavingsKind.BOND -> 2
                    else -> 1
                }
                coupon(startingBalance, rateBps, frequency)
            } else {
                val months = monthsHeldInFy(acquiredOn, date)
                if (kind == SavingsKind.RD || kind == SavingsKind.FD) {
                    quarterlyCompoundAccrual(MoneyScaled(projected), rateBps, months)
                } else annualAccrual(MoneyScaled(projected), rateBps, months)
            }
            if (mode == SavingsInterestMode.COMPOUND) {
                projected = Math.addExact(projected, accrual.raw)
            }
            if (accrual.raw > 0 && date !in postedDates) {
                results += SavingsInterestCredit(date, accrual, estimated = true)
            }
        }
        if (results.isEmpty()) return null
        return SavingsInterestReview(
            credits = results,
            projectedBalance = if (mode == SavingsInterestMode.COMPOUND)
                MoneyScaled(projected) else null,
            assumption = "Historical balances and rate changes are not fully known; confirm against the passbook.",
        )
    }
}
