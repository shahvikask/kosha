package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Material3 form, fictional holding; no market quote is fetched. */
class NativeM27EventFormInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val holding = NativeBooksRepository.Holding(
        "fictional-holding", "owner-a", "Fictional Owner", null, "fictional-instrument",
        "Example holding", "EQUITY", "Other", 100_000_000L, 10_000L,
        "2024-01-01", "USER_ENTRY", 10_000L, false, false, false, null)

    private fun form(onSave: (NativeBooksRepository.NewEvent) -> Unit) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EventForm(listOf(holding), false,
                        loadHistory = { emptyList() }, add = onSave, price = { _, _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("choice-Holding").performClick()
        compose.onNodeWithText("Example holding • Fictional Owner").performClick()
    }

    @Test fun cashDividendNeverRetainsHiddenUnitsOrPriceAndNeedsReview() {
        var saved: NativeBooksRepository.NewEvent? = null
        form { saved = it }
        compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
        compose.onNodeWithText("DIVIDEND").performClick()
        compose.onNodeWithTag("m27-event-units").assertDoesNotExist()
        compose.onNodeWithTag("m27-event-price").assertDoesNotExist()
        compose.onNodeWithTag("m27-event-amount").performScrollTo().performTextReplacement("12.5")
        compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithTag("m27-confirm-event").performScrollTo().performClick()
        assertEquals("DIVIDEND", saved!!.kind)
        assertEquals(0L, saved!!.quantityScaled)
        assertEquals(0L, saved!!.unitPriceMinor)
        assertEquals(125_000L, saved!!.amountMinor)
    }

    @Test fun splitIsExactRatioOnlyAndCannotSaveCashAmount() {
        var saved: NativeBooksRepository.NewEvent? = null
        form { saved = it }
        compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
        compose.onNodeWithText("SPLIT").performClick()
        compose.onNodeWithTag("m27-event-amount").assertDoesNotExist()
        compose.onNodeWithTag("m27-event-units").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("m27-event-price").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-event").performScrollTo().performClick()
        assertEquals("SPLIT", saved!!.kind)
        assertEquals(3L, saved!!.quantityScaled)
        assertEquals(2L, saved!!.unitPriceMinor)
        assertEquals(0L, saved!!.amountMinor)
        assertEquals(0L, saved!!.chargesMinor)
    }
}
