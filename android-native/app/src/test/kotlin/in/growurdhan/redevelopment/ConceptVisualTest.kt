package `in`.growurdhan.redevelopment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose semantics for the concept screens. Bitmap capture is not asserted:
 * captureToImage timed out under Robolectric LEGACY graphics, and NATIVE
 * graphics stopped the test process before a result was written.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-mdpi")
class ConceptVisualTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lockedScreenHasNoVaultAction() {
        rule.setContent {
            GudTheme(darkTheme = false) { UnlockScreen(onViewSample = {}) }
        }
        rule.onNodeWithText("Locked").assertIsDisplayed()
        rule.onNodeWithText("Vault remains locked. No book is open.").assertIsDisplayed()
        rule.onNodeWithText("There is no password field. This build has no vault to open.").assertIsDisplayed()
        rule.onNodeWithText("View sample layout").assertIsDisplayed()
    }

    @Test
    fun sampleHomeKeepsTheLockVisible() {
        rule.setContent {
            GudTheme(darkTheme = true) {
                HomeScreen(onOpenHolding = {}, onReturnToLocked = {})
            }
        }
        rule.onNodeWithText("Vault remains locked. No book is open.").assertIsDisplayed()
        rule.onNodeWithText("Sample figure. Not a ledger balance.").assertIsDisplayed()
        rule.onNodeWithText("Return to locked screen").assertIsDisplayed()
    }

    @Test
    fun sampleHoldingDoesNotInventACost() {
        rule.setContent {
            GudTheme(darkTheme = false) {
                HoldingDetailScreen(onBack = {}, onReturnToLocked = {})
            }
        }
        rule.onNodeWithText("Vault remains locked. No book is open.").assertIsDisplayed()
        rule.onNodeWithText("Acquisition cost unknown. This sample does not invent a cost.").assertIsDisplayed()
        rule.onNodeWithText("Return to locked screen").assertIsDisplayed()
    }
}
