package `in`.growurdhan.redevelopment

import java.math.BigInteger

/** No unscoped cross-owner search, archived-state ambiguity or floating-point sort. */
internal object NativeHoldingListFilter {
    const val ALL_ACCOUNTS = ""
    const val NO_ACCOUNT = "__unassigned__"
    private val statuses = setOf("All", "Active", "Archived")
    private val sorts = setOf("Name", "Recorded value", "Recent price", "Category")

    private fun recordedValue(holding: NativeBooksRepository.Holding): BigInteger =
        BigInteger.valueOf(holding.quantityScaled)
            .multiply(BigInteger.valueOf(holding.marketPriceMinor))
            .divide(BigInteger.valueOf(100_000_000L))

    fun apply(holdings: List<NativeBooksRepository.Holding>,
        ownerId: String, category: String, accountId: String,
        status: String, search: String, sort: String): List<NativeBooksRepository.Holding> {
        require(status in statuses && sort in sorts) { "Unknown holding filter" }
        val query = search.trim()
        val scoped = holdings.filter { h ->
            (ownerId.isEmpty() || h.personId == ownerId) &&
                (category == "All" || h.category == category) &&
                (accountId.isEmpty() || if (accountId == NO_ACCOUNT) h.accountId == null
                    else h.accountId == accountId) &&
                (status == "All" || (status == "Archived") == h.archived) &&
                (query.isEmpty() || h.name.contains(query, true) ||
                    h.subcategory.contains(query, true) ||
                    h.personName.contains(query, true))
        }
        // Stable deterministic tie-breaking is essential for repeatable review/history.
        return when (sort) {
            "Recorded value" -> scoped.sortedWith(
                compareByDescending<NativeBooksRepository.Holding> { recordedValue(it) }
                    .thenBy { it.name.lowercase() }.thenBy { it.id })
            "Recent price" -> scoped.sortedWith(
                compareByDescending<NativeBooksRepository.Holding> { it.priceDate.orEmpty() }
                    .thenBy { it.name.lowercase() }.thenBy { it.id })
            "Category" -> scoped.sortedWith(
                compareBy<NativeBooksRepository.Holding> { it.category }
                    .thenBy { it.name.lowercase() }.thenBy { it.id })
            else -> scoped.sortedWith(compareBy<NativeBooksRepository.Holding> { it.name.lowercase() }
                .thenBy { it.id })
        }
    }
}
