package `in`.growurdhan.redevelopment

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.BrandSourceTokens
import `in`.growurdhan.model.SemanticTokens
import kotlin.math.pow
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
        for (tab in nativeMainDestinations) {
            val b = compose.onNodeWithTag("books-tab-$tab").fetchSemanticsNode().boundsInRoot
            assertTrue("$tab overflowed 360dp visual panel", b.left >= root.left - 1f &&
                b.right <= root.right + 1f)
        }
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(requireNotNull(target.getExternalFilesDir("g14-evidence")), file)
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

    /** A synthetic 120-row actual Compose holdings list, never a vault snapshot. */
    private fun captureSyntheticHoldings(dark: Boolean, widthDp: Int,
        scale: Float, file: String) {
        val rows = (0 until 120).map { index ->
            val num = index.toString().padStart(3, '0')
            NativeBooksRepository.Holding(
                "synthetic-$num", "synthetic-member", "Synthetic Member",
                null, "instrument-$num", "Synthetic Long Fund Name $num",
                "MUTUAL_FUND", "Equity fund", 0L, 0L, null, null, 0L,
                true, true, false, null)
        }
        compose.setContent {
            GudTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    Column(Modifier.width(widthDp.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("g14-holdings-screen")) {
                        NativeOwnerLensStatus("Synthetic Member")
                        NativeBooksNavigation("Holdings") {}
                        NativeHoldingsLazyList(rows) { item ->
                            Text(item.name + " • cost unknown • needs review",
                                modifier = Modifier.testTag("g14-holding-${item.id}"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("g14-holdings-screen").assertExists()
        compose.onNodeWithTag("g14-holding-synthetic-000").assertExists()
        compose.onNodeWithTag("g14-holding-synthetic-119").assertDoesNotExist()
        val nav = compose.onNodeWithTag("books-nav").fetchSemanticsNode().boundsInRoot
        for (tab in nativeMainDestinations) {
            val bounds = compose.onNodeWithTag("books-tab-$tab")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Nav button horizontally clipped: $tab",
                bounds.left >= nav.left - 1f && bounds.right <= nav.right + 1f)
            assertTrue("Navigation action below 48dp: $tab", bounds.height >= 48f)
        }
        val image = compose.onNodeWithTag("g14-holdings-screen")
            .captureToImage().asAndroidBitmap()
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(requireNotNull(root.getExternalFilesDir("g14-evidence")), file)
        path.outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        assertTrue("Synthetic holdings PNG evidence missing", path.length() > 512)
    }

    @Test fun syntheticHoldingsLight360dpLargeFontScreenshot() =
        captureSyntheticHoldings(false, 360, 2f, "g14-holdings-light-360dp-2x.png")

    @Test fun syntheticHoldingsDark360dpLargeFontScreenshot() =
        captureSyntheticHoldings(true, 360, 2f, "g14-holdings-dark-360dp-2x.png")

    @Test fun syntheticHoldingsDark412dpScreenshot() =
        captureSyntheticHoldings(true, 412, 1f, "g14-holdings-dark-412dp-1x.png")

    @Test fun syntheticHoldingsLight840dpExpandedScreenshot() =
        captureSyntheticHoldings(false, 840, 1f, "g14-holdings-light-840dp-1x.png")

    /** Captures only isolated synthetic Compose fixtures, never a logged-in vault. */
    private fun saveFixture(tag: String, file: String) {
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(requireNotNull(root.getExternalFilesDir("g14-evidence")), file)
        screenshot.outputStream().use {
            assertTrue("Synthetic screenshot PNG failed", image.compress(
                Bitmap.CompressFormat.PNG, 100, it))
        }
        assertTrue("Synthetic screenshot was empty", screenshot.length() > 512)
    }

    @Test fun syntheticHoldingWizardReviewLight360dpLargeFontScreenshot() {
        val owner = NativeBooksRepository.Person("synthetic-owner", "Synthetic Owner",
            "Self", "INDIVIDUAL", true, true, null)
        compose.setContent {
            GudTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Column(Modifier.width(360.dp).height(740.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .testTag("g14-wizard-screen")) {
                        HoldingForm(listOf(owner), emptyList(), false) {}
                    }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performScrollTo().performClick()
        compose.onNodeWithText("Synthetic Owner").performClick()
        compose.onNodeWithTag("m27-holding-name").performScrollTo()
            .performTextInput("Synthetic long issuer — no real account")
        compose.onNodeWithTag("m27-holding-date").performScrollTo()
            .performTextReplacement("2024-02-29")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        compose.onNodeWithText("Confirm holding before saving").assertExists()
        compose.onNodeWithText("Acquisition cost: Unknown (no inferred basis)").assertExists()
        saveFixture("g14-wizard-screen", "g14-wizard-light-360dp-2x-review.png")
    }

    @Test fun syntheticCashEventReviewDark412dpLargeFontScreenshot() {
        val h = NativeBooksRepository.Holding("synthetic-holding", "synthetic-owner",
            "Synthetic Owner", null, "synthetic-instrument", "Synthetic bond",
            "FIXED_INCOME", "Bond / GOI", 0L, 10_000L, "2025-01-01",
            "USER_ENTRY", 0L, true, true, false, null)
        compose.setContent {
            GudTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Column(Modifier.width(412.dp).height(740.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .testTag("g14-event-screen")) {
                        EventForm(listOf(h), false, loadHistory = { emptyList() },
                            add = {}, price = { _, _, _ -> })
                    }
                }
            }
        }
        compose.onNodeWithTag("choice-Holding").performScrollTo().performClick()
        compose.onNodeWithText("Synthetic bond • Synthetic Owner").performClick()
        compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
        compose.onNodeWithText("DIVIDEND").performClick()
        compose.onNodeWithTag("m27-event-date").performScrollTo()
            .performTextReplacement("2025-01-02")
        compose.onNodeWithTag("m27-event-amount").performScrollTo()
            .performTextReplacement("12.5")
        compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
        compose.onNodeWithText("Confirm immutable event").assertExists()
        saveFixture("g14-event-screen", "g14-trade-dark-412dp-2x-review.png")
    }

    @Test fun semanticTextTokensMeetFourPointFiveToOneContrastInBothThemes() {
        fun luminance(hex: String): Double {
            val color = android.graphics.Color.parseColor(hex)
            fun linear(value: Int): Double {
                val fraction = value / 255.0
                return if (fraction <= 0.04045) fraction / 12.92
                    else ((fraction + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(android.graphics.Color.red(color)) +
                0.7152 * linear(android.graphics.Color.green(color)) +
                0.0722 * linear(android.graphics.Color.blue(color))
        }
        val pairs = listOf(
            "light background" to (BrandSourceTokens.LIGHT_TEXT to BrandSourceTokens.LIGHT_BACKGROUND),
            "light surface" to (BrandSourceTokens.LIGHT_TEXT to BrandSourceTokens.LIGHT_SURFACE),
            "light muted" to (BrandSourceTokens.LIGHT_MUTED to BrandSourceTokens.LIGHT_SURFACE),
            "light primary" to (SemanticTokens.LIGHT_ON_PRIMARY to SemanticTokens.LIGHT_PRIMARY_CONTAINER),
            "dark background" to (BrandSourceTokens.DARK_TEXT to BrandSourceTokens.DARK_BACKGROUND),
            "dark card" to (BrandSourceTokens.DARK_TEXT to BrandSourceTokens.DARK_CARD),
            "dark muted" to (BrandSourceTokens.DARK_MUTED to BrandSourceTokens.DARK_CARD),
            "dark primary" to (SemanticTokens.DARK_ON_PRIMARY to SemanticTokens.DARK_PRIMARY))
        pairs.forEach { (name, colors) ->
            val a = luminance(colors.first)
            val b = luminance(colors.second)
            val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
            assertTrue("G14 semantic text contrast below 4.5:1 for $name: $ratio",
                ratio >= 4.5)
        }
    }

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
