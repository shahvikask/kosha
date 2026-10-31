package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic control-only accessibility test; no private books or vault data. */
class NativeBooksNavigationAccessibilityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactPhoneTwoHundredPercentTextWrapsAndAnnouncesSelectedTab() {
        val tab = mutableStateOf("Home")
        compose.setContent {
            GudTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(360.dp)) {
                        NativeBooksNavigation(tab.value) { tab.value = it }
                    }
                }
            }
        }
        compose.onNodeWithTag("books-tab-Home").assertIsSelected()
        compose.onNodeWithTag("books-tab-Statements").assertIsNotSelected()

        // All eight destinations must stay inside a 360dp viewport at 200% font.
        val nav = compose.onNodeWithTag("books-nav").fetchSemanticsNode().boundsInRoot
        for (name in nativeMainDestinations) {
            val rect = compose.onNodeWithTag("books-tab-$name")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$name spills left of compact viewport", rect.left >= nav.left - 1f)
            assertTrue("$name spills right of compact viewport", rect.right <= nav.right + 1f)
        }
        compose.onNodeWithTag("books-tab-Statements").performClick()
        compose.onNodeWithTag("books-tab-Statements").assertIsSelected()
        compose.onNodeWithTag("books-tab-Home").assertIsNotSelected()
    }

    @Test fun navigationTabRestoresAcrossRotationAndLockReturnsToUnlock() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            GudTheme {
                var tab by rememberSaveable { mutableStateOf("Home") }
                var unlocked by rememberSaveable { mutableStateOf(true) }
                Column {
                    if (unlocked) {
                        NativeBooksNavigation(tab) { tab = it }
                        Button(onClick = { tab = "Home"; unlocked = false },
                            modifier = Modifier.testTag("test-lock")) { Text("Lock vault") }
                    } else {
                        Text("Unlock vault", modifier = Modifier.testTag("test-unlock"))
                    }
                }
            }
        }
        compose.onNodeWithTag("books-tab-Insights").performClick()
        compose.onNodeWithTag("books-tab-Insights").assertIsSelected()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("books-tab-Insights").assertIsSelected()
        compose.onNodeWithTag("test-lock").performClick()
        compose.onNodeWithTag("test-unlock").assertExists()
        compose.onNodeWithTag("books-tab-Insights").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("test-unlock").assertExists()
    }
}
