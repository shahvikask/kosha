package `in`.growurdhan.redevelopment

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Rule
import org.junit.Test

/**
 * Layoutlib screenshots. This test does not use Robolectric, captureToImage,
 * or View.draw.
 */
class ConceptScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
    )

    @Test
    fun lockedLight() {
        paparazzi.snapshot {
            GudTheme(darkTheme = false) { UnlockScreen(onViewSample = {}) }
        }
    }

    @Test
    fun sampleHomeDark() {
        paparazzi.snapshot {
            GudTheme(darkTheme = true) {
                HomeScreen(onOpenHolding = {}, onReturnToLocked = {})
            }
        }
    }

    @Test
    fun sampleHoldingLight() {
        paparazzi.snapshot {
            GudTheme(darkTheme = false) {
                HoldingDetailScreen(onBack = {}, onReturnToLocked = {})
            }
        }
    }
}
