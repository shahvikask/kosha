package `in`.growurdhan.redevelopment

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

/**
 * ONLY the isolated, entirely synthetic navigation components are captured.
 * Do not capture an admitted vault, account record, actual PAN or financial data.
 * The shipping app's secure-window policy must not be bypassed for screenshots.
 */
class NativeM26VisualEvidenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun captureSyntheticNavigation(dark: Boolean, file: String) {
        compose.setContent {
            GudTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Column(
                        Modifier.width(360.dp).background(MaterialTheme.colorScheme.background)
                            .testTag("g14-visual-content"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NativeOwnerLensStatus(if (dark) "Synthetic Member" else null)
                        NativeBooksNavigation("Home") {}
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("g14-visual-content").fetchSemanticsNode().boundsInRoot
        for (tab in listOf("Home", "Holdings", "Family", "Accounts", "Goals",
                "Records", "Documents", "Settings")) {
            val b = compose.onNodeWithTag("books-tab-$tab").fetchSemanticsNode().boundsInRoot
            assertTrue("$tab overflowed 360dp visual panel", b.left >= root.left - 1f &&
                b.right <= root.right + 1f)
        }
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = java.io.File(target.cacheDir, file)
        val image = compose.onNodeWithTag("g14-visual-content")
            .captureToImage().asAndroidBitmap()
        // Assert *rendered* scope-text contrast, not just theme-token contrast.
        // These pixels are above the first navigation button: no golden buttons
        // can accidentally mask a black-on-black scope label regression.
        var readableForeground = 0
        for (y in 0 until minOf(70, image.height)) {
            for (x in 0 until image.width) {
                val pixel = image.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                val total = r + g + b
                if ((dark && total >= 480) || (!dark && total <= 225)) {
                    readableForeground++
                }
            }
        }
        assertTrue("Owner-lens label has low rendered contrast in " +
            (if (dark) "dark" else "light") + " mode", readableForeground >= 80)
        screenshot.outputStream().use {
            assertTrue("Could not create safe PNG evidence", image.compress(
                Bitmap.CompressFormat.PNG, 100, it))
        }
        assertTrue("Visual PNG evidence missing", screenshot.length() > 512)
    }

    @Test fun syntheticLight360dpTwoHundredPercentScreenshot() =
        captureSyntheticNavigation(false, "g14-nav-light-360dp-2x.png")

    @Test fun syntheticDark360dpTwoHundredPercentScreenshot() =
        captureSyntheticNavigation(true, "g14-nav-dark-360dp-2x.png")

    @Test fun memberLensAnnouncementUpdatesForTalkBack() {
        val member = mutableStateOf<String?>(null)
        compose.setContent {
            GudTheme {
                NativeOwnerLensStatus(member.value)
            }
        }
        compose.onNodeWithText("Viewing Whole household financial records").assertExists()
        val announced = SemanticsMatcher.expectValue(
            SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        compose.onNodeWithTag("owner-lens-status").assert(announced)
        compose.runOnIdle { member.value = "Synthetic Member" }
        compose.onNodeWithText("Viewing Member: Synthetic Member financial records")
            .assertExists()
        compose.onNodeWithTag("owner-lens-status").assert(announced)
    }
}
