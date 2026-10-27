package `in`.growurdhan.redevelopment

import org.junit.Assert.*
import org.junit.Test

/** Synthetic holding list scopes. Does not access private user data. */
class NativeM27HoldingFilterInstrumentedTest {
    private fun h(id: String, owner: String, account: String?, name: String,
        category: String = "EQUITY", archived: Boolean = false, qty: Long = 0L,
        price: Long = 0L) = NativeBooksRepository.Holding(
            id, owner, if (owner == "a") "Owner A" else "Owner B",
            account, "instrument-$id", name, category, "Other", qty, price,
            null, null, 0L, true, false, archived, null)

    @Test fun accountAndArchivedFiltersAreAppliedAfterOwnerIsolation() {
        val rows = listOf(
            h("one", "a", "acct-a", "Own Equity", qty = 100_000_000L),
            h("arch", "a", null, "Old Holding", archived = true),
            h("foreign", "b", "acct-b", "Foreign Holding", archived = true))
        val selected = NativeHoldingListFilter.apply(rows, "a", "All",
            NativeHoldingListFilter.NO_ACCOUNT, "Archived", "", "Name")
        assertEquals(listOf("arch"), selected.map { it.id })
        val allA = NativeHoldingListFilter.apply(rows, "a", "All", "", "All", "", "Name")
        assertEquals(2, allA.size)
        assertFalse(allA.any { it.personId == "b" })
        val noMatch = NativeHoldingListFilter.apply(rows, "a", "MUTUAL_FUND",
            "", "All", "", "Name")
        assertTrue(noMatch.isEmpty())
    }

    @Test fun valueSortUsesExactBigIntegerAndStableNamesWithoutOverflow() {
        val rows = listOf(
            h("large", "a", null, "Very large", qty = Long.MAX_VALUE, price = Long.MAX_VALUE),
            h("small", "a", null, "Small", qty = 100_000_000L, price = 10_000L),
            h("zero", "a", null, "Empty", qty = 0L))
        val filtered = NativeHoldingListFilter.apply(rows, "a", "All", "", "Active",
            "", "Recorded value")
        assertEquals(listOf("large", "small", "zero"), filtered.map { it.id })
        assertEquals(3, filtered.size)
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingListFilter.apply(rows, "a", "All", "", "Active", "", "Made-up sort")
        }
    }
}
