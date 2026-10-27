package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/**
 * P11 evidence-scoped monthly PPF/SSY interest *comparison*, never a deposit,
 * interest ledger posting, statutory rate database or independently verified
 * issuer passbook. Frozen interest.ts one-contribution simplified projection
 * remains a distinct, unchanged product calculation.
 *
 * Actual bank/issuer posting rounding may differ. Four quarterly annual-rate
 * declarations, opening/closing statement references and every dated deposit
 * must come from the owner; the app does not infer missing records or rates.
 * Withdrawal, fee and mid-year opening scenarios are intentionally unsupported.
 */
object NativePpfSsyPassbookAccrual {
    enum class Scheme { PPF, SSY }
    data class Deposit(val ownerId: String, val holdingId: String,
        val day: LocalDate, val amountMinor: Long,
        val entryId: String, val statementRef: String)
    data class QuarterRate(val effectiveOn: LocalDate, val annualBps: Int,
        val rateSourceRef: String)
    data class YearEvidence(
        val ownerId: String, val holdingId: String, val scheme: Scheme,
        val fyEnd: LocalDate, val openingBalanceMinor: Long,
        val openingSourceRef: String, val deposits: List<Deposit>,
        val quarters: List<QuarterRate>, val closingBalanceMinor: Long,
        val closingSourceRef: String
    )
    data class Month(val month: YearMonth, val lowestFromFifthMinor: Long,
        val rateBps: Int, val calculatedInterestMinor: Long)
    data class YearComparison(val fyEnd: LocalDate, val months: List<Month>,
        val depositsMinor: Long, val calculatedInterestMinor: Long,
        val calculatedClosingMinor: Long, val ownerEnteredClosingMinor: Long,
        val matchesEnteredClosing: Boolean)
    data class Series(val comparisons: List<YearComparison>,
        val totalCalculatedInterestMinor: Long, val calculatedClosingMinor: Long,
        val allEnteredClosingsMatch: Boolean)

    private val annualDenominator = BigInteger.valueOf(120_000L)
    private fun supplied(s: String) = s.isNotBlank() && s.length <= 160 &&
        s.none { it.isISOControl() }

    fun compare(years: List<YearEvidence>, asOf: LocalDate): Series {
        require(years.isNotEmpty() && years.size <= 80) { "Explicit FY evidence required" }
        val first = years.first()
        require(supplied(first.ownerId) && supplied(first.holdingId))
        val results = mutableListOf<YearComparison>()
        var previous: YearEvidence? = null
        var calculatedPriorClose: Long? = null
        var totalInterest = 0L
        for (year in years) {
            require(year.ownerId == first.ownerId && year.holdingId == first.holdingId &&
                year.scheme == first.scheme) { "Mixed owner, holding or scheme" }
            require(year.fyEnd.monthValue == 3 && year.fyEnd.dayOfMonth == 31 &&
                !year.fyEnd.isAfter(asOf)) { "Only completed 31 March FYs" }
            val fyStart = LocalDate.of(year.fyEnd.year - 1, 4, 1)
            require(year.openingBalanceMinor >= 0L && year.closingBalanceMinor >= 0L &&
                supplied(year.openingSourceRef) && supplied(year.closingSourceRef)) {
                "Owner-entered opening/closing passbook proof required"
            }
            previous?.let {
                require(year.fyEnd == it.fyEnd.plusYears(1) &&
                    year.openingBalanceMinor == calculatedPriorClose &&
                    year.openingBalanceMinor == it.closingBalanceMinor) {
                    "Missing FY or unreconciled closing/opening; no inferred carryover"
                }
            }
            val boundaries = listOf(fyStart, fyStart.plusMonths(3),
                fyStart.plusMonths(6), fyStart.plusMonths(9))
            require(year.quarters.size == 4 &&
                year.quarters.map { it.effectiveOn } == boundaries) {
                "Four explicit April/July/October/January rate versions required"
            }
            require(year.quarters.all { it.annualBps in 1..10_000 &&
                supplied(it.rateSourceRef) }) { "Missing or unsupported rate evidence" }

            val sorted = year.deposits.sortedWith(
                compareBy<Deposit> { it.day }.thenBy { it.entryId })
            require(sorted.map { it.entryId }.distinct().size == sorted.size &&
                sorted.size <= 500) { "Duplicate or excessive passbook row identifiers" }
            require(sorted.all { it.ownerId == first.ownerId &&
                it.holdingId == first.holdingId &&
                it.day >= fyStart && it.day <= year.fyEnd &&
                it.amountMinor > 0L &&
                supplied(it.entryId) && supplied(it.statementRef) }) {
                "Foreign, undated, unreferenced or non-deposit cash flow"
            }
            // Read-only comparison; no entry IDs are created, corrected or posted.
            var totalDeposited = 0L
            for (d in sorted) totalDeposited = Math.addExact(totalDeposited, d.amountMinor)
            val months = (0..11).map { index ->
                val start = fyStart.plusMonths(index.toLong())
                val fifth = start.plusDays(4L)
                val base = BigInteger.valueOf(year.openingBalanceMinor).add(
                    sorted.filter { !it.day.isAfter(fifth) }
                        .fold(BigInteger.ZERO) { acc, d ->
                            acc.add(BigInteger.valueOf(d.amountMinor))
                        })
                val rate = year.quarters[index / 3].annualBps
                val interest = base.multiply(BigInteger.valueOf(rate.toLong()))
                    .divide(annualDenominator).longValueExact()
                Month(YearMonth.from(start), base.longValueExact(), rate, interest)
            }
            var yearInterest = 0L
            for (month in months) yearInterest =
                Math.addExact(yearInterest, month.calculatedInterestMinor)
            val closing = Math.addExact(Math.addExact(
                year.openingBalanceMinor, totalDeposited), yearInterest)
            results += YearComparison(year.fyEnd, months, totalDeposited,
                yearInterest, closing, year.closingBalanceMinor,
                closing == year.closingBalanceMinor)
            totalInterest = Math.addExact(totalInterest, yearInterest)
            calculatedPriorClose = closing
            previous = year
        }
        return Series(results, totalInterest, requireNotNull(calculatedPriorClose),
            results.all { it.matchesEnteredClosing })
    }
}
