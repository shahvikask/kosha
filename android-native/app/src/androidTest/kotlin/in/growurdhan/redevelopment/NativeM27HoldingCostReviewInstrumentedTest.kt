package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated real Compose M27 wizard; all entries fictional, no private books or screenshots. */
class NativeM27HoldingCostReviewInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val owner = NativeBooksRepository.Person(
        "synthetic-owner", "Fictional Member", "Self", "INDIVIDUAL", true, true, null)

    private fun openForm(onSave: (NativeBooksRepository.NewHolding) -> Unit) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(owner), emptyList(), false, onSave)
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText("Fictional Member").performClick()
        compose.onNodeWithTag("m27-holding-name").performTextInput("Fictional Fund")
        compose.onNodeWithTag("m27-holding-units").performTextReplacement("1")
    }

    @Test fun defaultUnknownCostRequiresReviewAndDoesNotBorrowCurrentPrice() {
        var saved: NativeBooksRepository.NewHolding? = null
        openForm { saved = it }
        compose.onNodeWithTag("m27-confirm-holding").assertDoesNotExist()
        compose.onNodeWithText("Opening acquisition cost: Unknown. No purchase price or gain will be inferred.")
            .assertExists()
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithText("Acquisition cost: Unknown (no inferred basis)").assertExists()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertFalse(saved!!.knownOpeningCost)
        assertNull(saved!!.openingUnitPriceMinor)
        assertEquals(100_000_000L, saved!!.quantityScaled)
    }

    @Test fun knownCostNeedsExplicitPriceAndChangedDraftMustBeReviewedAgain() {
        var saved: NativeBooksRepository.NewHolding? = null
        openForm { saved = it }
        compose.onNodeWithTag("choice-Opening cost").performScrollTo().performClick()
        compose.onNodeWithText("Known").performClick()
        compose.onNodeWithTag("m27-review-holding").assertIsNotEnabled()
        compose.onNodeWithTag("m27-opening-cost").performScrollTo().performTextInput("12.34")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("m27-review-holding").performClick()
        compose.onNodeWithTag("m27-holding-name").performTextInput(" Revised")
        compose.onNodeWithTag("m27-confirm-holding").assertDoesNotExist()
        assertNull(saved)
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertTrue(saved!!.knownOpeningCost)
        assertEquals(123_400L, saved!!.openingUnitPriceMinor)
    }
}
