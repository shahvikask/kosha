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
 * Only dated, independently owner-referenced withdrawals are modeled; issuer
 * authorization, maturity/withdrawal eligibility and statutory limits remain
 * unverified. Fees and mid-year opening scenarios remain unsupported.
 */
object NativePpfSsyPassbookAccrual {
    enum class Scheme { PPF, SSY }
    data class Deposit(val ownerId: String, val holdingId: String,
        val day: LocalDate, val amountMinor: Long,
        val entryId: String, val statementRef: String)
    data class Withdrawal(val ownerId: String, val holdingId: String,
        val day: LocalDate, val amountMinor: Long,
        val entryId: String, val statementRef: String)
    data class QuarterRate(val effectiveOn: LocalDate, val annualBps: Int,
        val rateSourceRef: String)
    data class YearEvidence(
        val ownerId: String, val holdingId: String, val scheme: Scheme,
        val fyEnd: LocalDate, val openingBalanceMinor: Long,
        val openingSourceRef: String, val deposits: List<Deposit>,
        val quarters: List<QuarterRate>, val closingBalanceMinor: Long,
        val closingSourceRef: String,
        val withdrawals: List<Withdrawal> = emptyList(),
        // Owner-entered source value, NOT an official or certified scheme cap.
        val statedContributionLimitMinor: Long? = null,
        val contributionLimitSourceRef: String? = null
    )
    data class Month(val month: YearMonth, val lowestFromFifthMinor: Long,
        val rateBps: Int, val calculatedInterestMinor: Long)
    data class YearComparison(val fyEnd: LocalDate, val months: List<Month>,
        val depositsMinor: Long, val withdrawalsMinor: Long,
        val calculatedInterestMinor: Long,
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
        val usedEntryIds = mutableSetOf<String>()
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

            require(year.deposits.size + year.withdrawals.size <= 500) {
                "Excessive passbook rows"
            }
            require(year.deposits.all { it.ownerId == first.ownerId &&
                it.holdingId == first.holdingId &&
                it.day >= fyStart && it.day <= year.fyEnd &&
                it.amountMinor > 0L &&
                supplied(it.entryId) && supplied(it.statementRef) }) {
                "Foreign, undated, unreferenced or non-deposit cash flow"
            }
            require(year.withdrawals.all { it.ownerId == first.ownerId &&
                it.holdingId == first.holdingId &&
                it.day >= fyStart && it.day <= year.fyEnd &&
                it.amountMinor > 0L &&
                supplied(it.entryId) && supplied(it.statementRef) }) {
                "Foreign, undated, unreferenced or invalid withdrawal"
            }
            data class Cash(val day: LocalDate, val id: String,
                val minor: Long, val isDeposit: Boolean)
            val cash = (
                year.deposits.map { Cash(it.day, it.entryId, it.amountMinor, true) } +
                year.withdrawals.map { Cash(it.day, it.entryId, it.amountMinor, false) }
            ).sortedWith(compareBy<Cash> { it.day }.thenBy { it.id })
            require(cash.all { usedEntryIds.add(it.id) }) {
                "Duplicate statement entry across directions or financial years"
            }
            require(cash.groupBy { it.day }.values.none { group ->
                group.any { it.isDeposit } && group.any { !it.isDeposit }
            }) { "Same-day deposit/withdrawal ordering is not evidenced" }

            val totalDeposited = year.deposits.fold(0L) { acc, d ->
                Math.addExact(acc, d.amountMinor)
            }
            val totalWithdrawn = year.withdrawals.fold(0L) { acc, w ->
                Math.addExact(acc, w.amountMinor)
            }
            require((year.statedContributionLimitMinor == null) ==
                (year.contributionLimitSourceRef == null)) {
                "Owner-entered contribution cap and source must be paired"
            }
            year.statedContributionLimitMinor?.let { limit ->
                require(limit > 0L && supplied(requireNotNull(year.contributionLimitSourceRef)) &&
                    totalDeposited <= limit) {
                    "Owner-entered contribution limit exceeded or unsupported"
                }
            }
            // Independently check chronological cash sufficiency for every row;
            // same-day opposite directions fail instead of using guessed ordering.
            var running = BigInteger.valueOf(year.openingBalanceMinor)
            val ending = cash.map { item ->
                running = if (item.isDeposit) running.add(BigInteger.valueOf(item.minor))
                    else running.subtract(BigInteger.valueOf(item.minor))
                require(running.signum() >= 0) { "Negative sourced passbook balance" }
                running.longValueExact()
            }
            val months = (0..11).map { index ->
                val monthStart = fyStart.plusMonths(index.toLong())
                val fifth = monthStart.plusDays(4L)
                val monthEnd = monthStart.plusMonths(1).minusDays(1L)
                var lowest = BigInteger.valueOf(year.openingBalanceMinor)
                for (i in cash.indices) if (!cash[i].day.isAfter(fifth)) {
                    lowest = BigInteger.valueOf(ending[i])
                }
                var balanceAfterFifth = lowest
                for (i in cash.indices) {
                    val tx = cash[i]
                    if (tx.day.isAfter(fifth) && !tx.day.isAfter(monthEnd)) {
                        balanceAfterFifth = BigInteger.valueOf(ending[i])
                        if (balanceAfterFifth < lowest) lowest = balanceAfterFifth
                    }
                }
                val rate = year.quarters[index / 3].annualBps
                val interest = lowest.multiply(BigInteger.valueOf(rate.toLong()))
                    .divide(annualDenominator).longValueExact()
                Month(YearMonth.from(monthStart), lowest.longValueExact(), rate, interest)
            }
            var yearInterest = 0L
            for (month in months) yearInterest =
                Math.addExact(yearInterest, month.calculatedInterestMinor)
            val closing = Math.addExact(Math.subtractExact(
                Math.addExact(year.openingBalanceMinor, totalDeposited),
                totalWithdrawn), yearInterest)
            results += YearComparison(year.fyEnd, months, totalDeposited,
                totalWithdrawn, yearInterest, closing, year.closingBalanceMinor,
                closing == year.closingBalanceMinor)
            totalInterest = Math.addExact(totalInterest, yearInterest)
            calculatedPriorClose = closing
            previous = year
        }
        return Series(results, totalInterest, requireNotNull(calculatedPriorClose),
            results.all { it.matchesEnteredClosing })
    }
}
