package `in`.growurdhan.redevelopment

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.By
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL MainActivity, system Keyguard confirmation and Android DocumentsUI.
 * No synthetic owner identity or vault publication is injected into production.
 * These are fail-closed OS-boundary regressions, NOT the complete positive
 * new-device restore or a physical device acceptance test.
 *
 * The dedicated clean API35 emulator harness provisions a synthetic screen PIN.
 */
@RunWith(AndroidJUnit4::class)
class NativeV9RealOsJourneyInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun verifySecureEmulatorHostIsInteractive() {
        NativeV9SecureTestHost.requireUnlockedInteractiveDevice()
    }
    private val device by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }
    private val appPackage by lazy { InstrumentationRegistry.getInstrumentation().targetContext.packageName }

    private fun waitForOutsideApp(label: String) {
        assertTrue("$label must present a real external Android UI",
            device.wait(Until.gone(By.pkg(appPackage)), 20_000))
        val foreground = device.currentPackageName
        assertTrue("$label must not be a simulated in-app Compose dialog",
            !foreground.isNullOrBlank() && foreground != appPackage)
    }

    private fun waitForLocked() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Verify this device").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Verify this device").assertExists()
        compose.onNodeWithText("Verify native identity and preview").assertDoesNotExist()
        compose.onNodeWithText("Confirm one-time native recovery").assertDoesNotExist()
    }

    private fun completeActualSystemCredential() {
        // Deliberately generated, synthetic CI-only emulator PIN. Do not log it.
        device.executeShellCommand("input text 246810")
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Device verified. Enter your account credentials.")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun realSystemCredentialCancellationAndActivityRestartRemainLocked() {
        waitForLocked()
        compose.onNodeWithText("Verify this device").performClick()
        waitForOutsideApp("Keyguard confirmation")
        device.pressBack() // genuine OS cancellation, not a synthetic callback
        waitForLocked()
        compose.activityRule.scenario.recreate() // new Activity cannot reuse admission
        waitForLocked()
    }

    @Test fun realDocumentsUiCancellationRevokesFreshOsAdmissionAndPreservesNoArchive() {
        waitForLocked()
        compose.onNodeWithText("Verify this device").performClick()
        waitForOutsideApp("Keyguard confirmation")
        completeActualSystemCredential()
        compose.onNodeWithText("Restore an encrypted backup on a new device").performClick()
        compose.onNodeWithText("Restore a newer GUDNAT01 native encrypted backup").performClick()
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup").performClick()
        waitForOutsideApp("SAF ACTION_OPEN_DOCUMENT picker")
        device.pressBack() // actual external DocumentsUI cancellation
        waitForLocked()
        compose.onNodeWithText("Encrypted native archive staged").assertDoesNotExist()
        // Old admission is not reusable. The real OS challenge must be completed again.
        compose.onNodeWithText("Verify this device").performClick()
        waitForOutsideApp("second Keyguard confirmation")
        completeActualSystemCredential()
        compose.onNodeWithText("Restore an encrypted backup on a new device").assertExists()
        compose.onNodeWithText("Confirm one-time native recovery").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        waitForLocked()
    }
}
