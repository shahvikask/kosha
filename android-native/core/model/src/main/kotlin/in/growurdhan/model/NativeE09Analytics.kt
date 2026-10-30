package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate

/**
 * E09 exact, READ-ONLY concentration on already admissible priced assets.
 * No fabricated total net worth, inferred hybrid look-through or unknown basis.
 */
object NativeE09Analytics {
    private val scale = BigInteger.valueOf(10_000)
    data class Category(val name: String, val pricedMinor: BigInteger, val basisPoints: Int)
    data class Allocation(val categories: List<Category>, val totalMinor: BigInteger,
        val leadingCategory: String?, val leadingBasisPoints: Int,
        val liabilityRecordCountExcluded: Int)

    fun allocation(pricedCategories: Map<String, BigInteger>,
        pricedAssetMinor: BigInteger, excludedLiabilities: Int): Allocation {
        require(pricedAssetMinor.signum() >= 0 && excludedLiabilities >= 0)
        require(pricedCategories.keys.none { it.isBlank() || it == "LIABILITY" })
        require(pricedCategories.values.all { it.signum() >= 0 })
        require(pricedCategories.values.fold(BigInteger.ZERO, BigInteger::add) ==
            pricedAssetMinor) { "Only reconciled priced subset is eligible" }
        if (pricedAssetMinor.signum() == 0) return Allocation(emptyList(),
            BigInteger.ZERO, null, 0, excludedLiabilities)
        val positive = pricedCategories.filterValues { it.signum() > 0 }.toSortedMap()
        val floors = positive.map { (name, value) ->
            val numerator = value.multiply(scale)
            val div = numerator.divideAndRemainder(pricedAssetMinor)
            Triple(name, div[0].toInt(), div[1])
        }
        var remaining = 10_000 - floors.sumOf { it.second }
        require(remaining in 0..floors.size) { "Allocation rounding invariant" }
        val extra = floors.sortedWith(compareByDescending<Triple<String, Int, BigInteger>> {
            it.third
        }.thenBy { it.first }).take(remaining).map { it.first }.toSet()
        val rows = floors.map { (name, bps, _) ->
            Category(name, positive.getValue(name), bps + if (name in extra) 1 else 0)
        }
        require(rows.sumOf { it.basisPoints } == 10_000)
        val leader = rows.sortedWith(compareByDescending<Category> { it.basisPoints }
            .thenBy { it.name }).first()
        return Allocation(rows, pricedAssetMinor, leader.name, leader.basisPoints,
            excludedLiabilities)
    }

    data class GoalProgress(
        val id: String, val name: String, val targetMinor: BigInteger?,
        val linkedHoldingCount: Int, val excludedLinkedHoldings: Int,
        val completeValuedMinor: BigInteger?
    ) {
        val basisPoints: Int? get() = NativeE09Analytics.verifiedGoalProgress(targetMinor,
            completeValuedMinor, linkedHoldingCount > 0 &&
                excludedLinkedHoldings == 0)
    }

    /** Explicit owner-entered cash evidence, not a verified bank or investment transaction. */
    enum class CashKind { CONTRIBUTION, EXPENSE }
    data class CashEntry(val id: String, val ownerId: String, val kind: CashKind,
        val day: String, val amountMinor: Long, val note: String)
    data class CashYear(val year: Int, val contributionMinor: BigInteger,
        val expenseMinor: BigInteger)
    fun manualCashYears(rows: List<CashEntry>, memberId: String): List<CashYear> {
        require(rows.map { it.id }.distinct().size == rows.size)
        val years = sortedMapOf<Int, Pair<BigInteger, BigInteger>>()
        for (r in rows) {
            require(r.id.isNotBlank() && r.ownerId.isNotBlank() &&
                (memberId.isEmpty() || memberId == r.ownerId) && r.amountMinor > 0 &&
                r.note.length <= 120 && r.note.none { it.isISOControl() })
            val date = LocalDate.parse(r.day)
            require(date.toString() == r.day && date.year in 1900..2200)
            val p = years[date.year] ?: (BigInteger.ZERO to BigInteger.ZERO)
            val cash = BigInteger.valueOf(r.amountMinor)
            years[date.year] = when (r.kind) {
                CashKind.CONTRIBUTION -> (p.first + cash) to p.second
                CashKind.EXPENSE -> p.first to (p.second + cash)
            }
        }
        return years.map { (year, cash) -> CashYear(year, cash.first, cash.second) }
    }

    data class Year(val year: Int, val recordedIncomeMinor: BigInteger,
        val realizedKnownMinor: BigInteger)
    /** Cash contributions/expenses are not inferred from holdings transactions. */
    fun recordedYears(income: Map<Int, BigInteger>, realized: Map<Int, BigInteger>):
        List<Year> {
        require(income.values.all { it.signum() >= 0 })
        require((income.keys + realized.keys).all { it in 1900..2200 })
        return (income.keys + realized.keys).toSortedSet().map {
            Year(it, income[it] ?: BigInteger.ZERO, realized[it] ?: BigInteger.ZERO)
        }
    }

    /**
     * Goal progress is available only with explicitly verified, complete,
     * non-overlapping allocations; never treat missing prices as zero.
     */
    fun verifiedGoalProgress(targetMinor: BigInteger?,
        eligibleMinor: BigInteger?, allLinkedHoldingsValued: Boolean): Int? {
        if (targetMinor == null || targetMinor.signum() <= 0 ||
            eligibleMinor == null || eligibleMinor.signum() < 0 ||
            !allLinkedHoldingsValued) return null
        return eligibleMinor.multiply(scale).divide(targetMinor)
            .min(scale).toInt()
    }
}
