package `in`.growurdhan.redevelopment

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No statement bytes or real account data: prove UI never leaks household-wide source history. */
@RunWith(AndroidJUnit4::class)
class NativeHouseholdDocumentAccessGateInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memberLensCannotDisplayOrStartHouseholdDocumentReview() {
        val ownerFilter = mutableStateOf("selected-member")
        compose.setContent {
            GudTheme {
                NativeHouseholdDocumentAccessGate(ownerFilter.value) {
                    Text("Household-only original source")
                }
            }
        }
        compose.onNodeWithText("Household-only original source").assertDoesNotExist()
        compose.onNodeWithText("Statement imports and document history span the whole household.",
            substring = true).assertExists()

        compose.runOnIdle { ownerFilter.value = "" }
        compose.onNodeWithText("Household-only original source").assertExists()

        compose.runOnIdle { ownerFilter.value = "selected-member" }
        compose.onNodeWithText("Household-only original source").assertDoesNotExist()
    }
}
