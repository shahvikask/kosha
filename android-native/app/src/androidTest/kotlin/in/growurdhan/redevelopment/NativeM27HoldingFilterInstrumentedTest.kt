package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic holding list scopes. Does not access private user data. */
@RunWith(AndroidJUnit4::class)
class NativeM27HoldingFilterInstrumentedTest {
    @get:Rule val compose = createComposeRule()
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

    @Test fun lazyOwnerScopedHoldingsAt360dpAnd200PercentDarkTextAreVirtualized() {
        val rows = (0 until 220).map { index ->
            val number = index.toString().padStart(4, '0')
            h("a-$number", "a", null, "Holding $number")
        } + h("foreign", "b", "acct-b", "Foreign Holding")
        val filtered = NativeHoldingListFilter.apply(rows, "a", "All",
            "", "All", "", "Name")
        assertEquals(220, filtered.size)
        assertTrue(filtered.all { it.personId == "a" })
        assertEquals(listOf("foreign"), NativeHoldingListFilter.apply(
            rows, "b", "All", "", "All", "", "Name").map { it.id })
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(360.dp)) {
                        NativeHoldingsLazyList(filtered) { holding ->
                            Text(holding.name, modifier = Modifier.testTag("m27-lazy-${holding.id}"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("m27-holdings-list").assertExists()
        compose.onNodeWithTag("m27-lazy-a-0000").assertExists()
        compose.onNodeWithTag("m27-lazy-a-0219").assertDoesNotExist()
        compose.onNodeWithTag("m27-holdings-list").performScrollToIndex(219)
        compose.onNodeWithTag("m27-lazy-a-0219").assertExists()
        compose.onNodeWithTag("m27-lazy-foreign").assertDoesNotExist()
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
