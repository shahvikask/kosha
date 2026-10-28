package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Real holding wizard Compose interactions; synthetic data only. */
class NativeM27SpecialistFormInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mutualFundTypedPlanIsReviewedAndSubmittedWithoutRawJsonEditor() {
        var saved: NativeBooksRepository.NewHolding? = null
        val owner = NativeBooksRepository.Person("fictional-owner", "Fictional Owner",
            "Self", "INDIVIDUAL", true, true, null)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(owner), emptyList(), false) { saved = it }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText("Fictional Owner").performClick()
        compose.onNodeWithTag("choice-Asset category").performScrollTo().performClick()
        compose.onNodeWithText("MUTUAL_FUND").performClick()
        compose.onNodeWithTag("choice-MF plan").performScrollTo().performClick()
        compose.onNodeWithText("DIRECT").performClick()
        compose.onNodeWithTag("m27-holding-name").performScrollTo().performTextInput("Synthetic MF")
        compose.onNodeWithTag("m27-holding-units").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithText("Confirm holding before saving").assertExists()
        // The owner must see exactly the reviewed specialist values before committing.
        compose.onNodeWithText("MF plan: DIRECT").assertExists()
        compose.onNodeWithText("Custody: No custody account").assertExists()
        compose.onNodeWithText("Issuer / institution: Not specified").assertExists()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertEquals("MUTUAL_FUND", saved!!.category)
        assertEquals("DIRECT", JSONObject(saved!!.detailsJson!!).getString("mfPlan"))
    }

    @Test fun bankFdWizardPreloadsSourceCumulativeQuarterlyCompounding() {
        var saved: NativeBooksRepository.NewHolding? = null
        val member = NativeBooksRepository.Person("fd-owner", "Fictional Depositor",
            "Self", "INDIVIDUAL", true, true, null)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(member), emptyList(), false) { saved = it }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText("Fictional Depositor").performClick()
        compose.onNodeWithTag("choice-Asset category").performScrollTo().performClick()
        compose.onNodeWithText("FIXED_INCOME").performClick()
        compose.onNodeWithTag("choice-Subcategory").performScrollTo().performClick()
        compose.onNodeWithText("Bank FD").performClick()
        compose.onNodeWithText("CUMULATIVE").assertExists()
        compose.onNodeWithTag("m27-holding-name").performScrollTo()
            .performTextInput("Fictional bank FD")
        compose.onNodeWithTag("m27-holding-units").performScrollTo()
            .performTextReplacement("0")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertEquals("FIXED_INCOME", saved!!.category)
        assertEquals("CUMULATIVE", JSONObject(saved!!.detailsJson!!).getString("compounding"))
    }

    private fun assertZeroCostCategory(category: String) {
        var saved: NativeBooksRepository.NewHolding? = null
        val owner = NativeBooksRepository.Person("category-owner", "Category Test Owner",
            "Self", "INDIVIDUAL", true, true, null)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(owner), emptyList(), false) { saved = it }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText("Category Test Owner").performClick()
        compose.onNodeWithTag("choice-Asset category").performScrollTo().performClick()
        compose.onNodeWithText(category).performClick()
        compose.onNodeWithTag("m27-holding-name").performScrollTo()
            .performTextInput("Fictional " + category)
        compose.onNodeWithTag("m27-holding-units").performScrollTo()
            .performTextReplacement("0")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithText("Acquisition cost: Unknown (no inferred basis)").assertExists()
        compose.onNodeWithText("Instrument: Fictional " + category +
            " • " + category + " / " + NativeAssetTaxonomy.subcategories.getValue(category).first())
            .assertExists()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertEquals(category, saved!!.category)
        assertEquals("category-owner", saved!!.personId)
        assertEquals(false, saved!!.knownOpeningCost)
        assertEquals(null, saved!!.openingUnitPriceMinor)
    }

    @Test fun equityCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("EQUITY")
    @Test fun goldCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("GOLD_SGB")
    @Test fun realEstateCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("REAL_ESTATE")
    @Test fun specialisedCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("SPECIALISED")
    @Test fun cashCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("CASH_BANK")
    @Test fun liabilityCategoryAllowsExplicitUnknownCost() = assertZeroCostCategory("LIABILITY")

    @Test fun invalidAcquisitionDateCannotBecomeApprovedHolding() {
        var saved: NativeBooksRepository.NewHolding? = null
        val owner = NativeBooksRepository.Person("invalid-date-owner", "Invalid Date Owner",
            "Self", "INDIVIDUAL", true, true, null)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(owner), emptyList(), false) { saved = it }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText("Invalid Date Owner").performClick()
        compose.onNodeWithTag("m27-holding-name").performScrollTo()
            .performTextInput("Synthetic date check")
        compose.onNodeWithTag("m27-holding-date").performScrollTo()
            .performTextReplacement("2026-02-30")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithText("Enter a valid acquisition date (YYYY-MM-DD).").assertExists()
        compose.onNodeWithTag("m27-confirm-holding").assertDoesNotExist()
        assertEquals(null, saved)
        compose.onNodeWithTag("m27-holding-date").performScrollTo()
            .performTextReplacement("2024-02-29")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(saved)
        assertEquals("2024-02-29", saved!!.date)
    }
}
