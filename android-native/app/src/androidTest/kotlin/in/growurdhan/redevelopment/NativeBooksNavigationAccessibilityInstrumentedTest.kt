package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Box
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
        compose.onNodeWithTag("books-tab-Records").assertIsNotSelected()

        // All eight destinations must stay inside a 360dp viewport at 200% font.
        val nav = compose.onNodeWithTag("books-nav").fetchSemanticsNode().boundsInRoot
        for (name in listOf("Home", "Holdings", "Family", "Accounts",
            "Goals", "Records", "Documents", "Settings")) {
            val rect = compose.onNodeWithTag("books-tab-$name")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$name spills left of compact viewport", rect.left >= nav.left - 1f)
            assertTrue("$name spills right of compact viewport", rect.right <= nav.right + 1f)
        }
        compose.onNodeWithTag("books-tab-Records").performClick()
        compose.onNodeWithTag("books-tab-Records").assertIsSelected()
        compose.onNodeWithTag("books-tab-Home").assertIsNotSelected()
    }
}
