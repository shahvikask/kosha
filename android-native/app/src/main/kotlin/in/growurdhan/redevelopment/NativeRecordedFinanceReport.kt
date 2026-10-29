package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativePortfolioMath
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * P24/E09 read-only recorded-evidence coverage; it is NOT tax liability,
 * verified market performance, total net worth or owner-independent data.
 * Financial arithmetic never rounds through Double or invents a missing basis.
 */
internal object NativeRecordedFinanceReport {
    private val quantityScale = BigInteger.valueOf(100_000_000L)
    data class Row(
        val holdingId: String,
        val ownerId: String,
        val category: String,
        val archived: Boolean,
        val needsReview: Boolean,
        val quantityScaled: Long,
        val priceMinor: Long,
        val priceDate: String?,
        val priceSource: String?,
        val events: List<NativePortfolioMath.Event>
    )
    data class Summary(
        val pricedAssetMinor: BigInteger,
        val pricedKnownBasisMinor: BigInteger,
        val knownUnrealizedMinor: BigInteger,
        val realizedKnownMinor: BigInteger,
        val realizedUnknownLots: Int,
        val incomeByCalendarYear: Map<Int, BigInteger>,
        val realizedByCalendarYear: Map<Int, BigInteger>,
        val pricedCategories: Map<String, BigInteger>,
        val unpricedActiveAssets: Int,
        val pricedUnknownBasisAssets: Int,
        val excludedLiabilities: Int,
        val excludedReviewAssets: Int,
        val archivedExcluded: Int,
        val pricedAssetCount: Int,
        val countKnownBasis: Int
    )

    /** Follow one correction chain to its root kind; reject cycles/foreign links. */
    private fun effectiveCashIncome(events: List<NativePortfolioMath.Event>):
        List<Pair<Int, BigInteger>> {
        val map = events.associateBy { it.id }
        require(map.size == events.size) { "Duplicate ledger event IDs" }
        val superseded = events.mapNotNull { it.supersedes }.toSet()
        return events.filter { it.id !in superseded }.mapNotNull { row ->
            var cursor = row
            val visited = mutableSetOf<String>()
            while (cursor.kind == "CORRECTION") {
                require(visited.add(cursor.id)) { "Correction cycle in income" }
                val target = map[cursor.supersedes]
                require(target != null && target.ownerId == row.ownerId &&
                    target.holdingId == row.holdingId) { "Cross-owner or missing correction" }
                cursor = target
            }
            if (cursor.kind !in setOf("DIVIDEND", "INTEREST")) return@mapNotNull null
            require(row.totalAmountMinor >= 0L && row.quantityScaled == 0L &&
                row.unitPriceMinor == 0L && row.chargesMinor == 0L) {
                "Invalid recorded cash income"
            }
            LocalDate.parse(row.date).year to BigInteger.valueOf(row.totalAmountMinor)
        }
    }

    fun summarize(rows: List<Row>, memberId: String, asOf: LocalDate): Summary {
        val scoped = rows.filter { memberId.isEmpty() || it.ownerId == memberId }
        require(scoped.map { it.holdingId }.distinct().size == scoped.size &&
            scoped.all { it.ownerId.isNotBlank() && it.holdingId.isNotBlank() }) {
            "Duplicate or invalid holding identity"
        }
        var priced = BigInteger.ZERO
        var basis = BigInteger.ZERO
        var unrealized = BigInteger.ZERO
        var realized = BigInteger.ZERO
        var unknownRealized = 0
        var unpriced = 0
        var unknownBasis = 0
        var liabilities = 0
        var excludedReview = 0
        var archived = 0
        var pricedCount = 0
        var basisCount = 0
        val income = sortedMapOf<Int, BigInteger>()
        val gains = sortedMapOf<Int, BigInteger>()
        val categories = sortedMapOf<String, BigInteger>()
        for (row in scoped) {
            require(row.priceMinor >= 0L && row.quantityScaled >= 0L) {
                "Invalid stored price or quantity"
            }
            require(row.events.all { it.ownerId == row.ownerId &&
                it.holdingId == row.holdingId }) {
                "Foreign ledger event in owner report"
            }
            val fold = NativePortfolioMath.fold(row.events, row.category)
            require(fold.errors.isEmpty() && fold.quantityScaled == row.quantityScaled) {
                "Ledger fold is inconsistent with stored holding quantity"
            }
            for (sale in fold.disposals) {
                val gainMinor = sale.gainMinor
                if (gainMinor == null) unknownRealized++
                else {
                    val gain = BigInteger.valueOf(gainMinor)
                    realized += gain
                    val year = LocalDate.parse(sale.sold).year
                    gains[year] = (gains[year] ?: BigInteger.ZERO) + gain
                }
            }
            for ((year, value) in effectiveCashIncome(row.events)) {
                income[year] = (income[year] ?: BigInteger.ZERO) + value
            }
            // Archives retain historical realized/income evidence, never current AUM.
            if (row.archived) { archived++; continue }
            if (row.category == "LIABILITY") { liabilities++; continue }
            if (row.quantityScaled == 0L) continue
            if (row.needsReview) { excludedReview++; continue }
            val dated = row.priceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val age = dated?.let { ChronoUnit.DAYS.between(it, asOf) }
            if (row.priceMinor == 0L || row.priceSource.isNullOrBlank() ||
                age == null || age < 0L || age >= 45L) {
                unpriced++
                continue
            }
            val value = BigInteger.valueOf(row.quantityScaled)
                .multiply(BigInteger.valueOf(row.priceMinor)).divide(quantityScale)
            priced += value
            pricedCount++
            categories[row.category] = (categories[row.category] ?: BigInteger.ZERO) + value
            if (fold.hasUnknownCost) unknownBasis++
            else {
                val known = BigInteger.valueOf(fold.knownCostMinor)
                basis += known
                unrealized += value - known
                basisCount++
            }
        }
        require(categories.values.fold(BigInteger.ZERO, BigInteger::add) == priced) {
            "Asset categories must reconcile to priced subset"
        }
        return Summary(priced, basis, unrealized, realized, unknownRealized,
            income.toMap(), gains.toMap(), categories.toMap(), unpriced, unknownBasis,
            liabilities, excludedReview, archived, pricedCount, basisCount)
    }
}
