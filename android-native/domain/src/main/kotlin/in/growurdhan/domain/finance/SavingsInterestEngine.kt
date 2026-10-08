package \`in\`.growurdhan.domain.finance

import \`in\`.growurdhan.core.model.MoneyScaled
import java.math.BigInteger
import java.time.LocalDate

/**
 * Reference inputs: src/lib/interest.ts, Jul–Sep 2026 small-savings rates
 * and FY 2025-26 EPF rate. Not a rate history or authoritative tax service.
 * Money and quantities remain scaled integers; no floating-point balances.
 */
enum class SavingsKind { SGB, PPF, EPF, SSY, FD, SCSS, POMIS, NSC, KVP, RD, FRSB, BOND, OTHER }
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
            ).longValueExact())
    }

    /** TS periodInterest; a payout is not compounded into the investment. */
    fun coupon(principal: MoneyScaled, rateBps: Int, payoutsPerYear: Int): MoneyScaled {
        require(rateBps in 0..100_000 && payoutsPerYear in 1..12)
        if (principal.raw <= 0 || rateBps == 0) return MoneyScaled(0)
        return MoneyScaled((
            BigInteger.valueOf(principal.raw) * BigInteger.valueOf(rateBps.toLong()) /
                BigInteger.valueOf(10_000L * payoutsPerYear)
            ).longValueExact())
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
}
