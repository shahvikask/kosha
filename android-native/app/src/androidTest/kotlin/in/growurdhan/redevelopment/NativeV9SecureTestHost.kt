package `in`.growurdhan.redevelopment

import android.app.KeyguardManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Test-only, fail-closed readiness check for the dedicated PIN-secured API35
 * emulator. The PIN is synthetic and provisioned by the targeted CI harness.
 *
 * Android can leave a newly secured emulator at the lock screen even after adb
 * input. In that state BOTH createComposeRule and createAndroidComposeRule
 * report "No compose hierarchies found". Never misreport such a device-host
 * failure as a successful UI assertion or disable the real Keyguard.
 */
internal object NativeV9SecureTestHost {
    fun requireUnlockedInteractiveDevice() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val keyguard = instrument.targetContext.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrument)
        assertTrue("Secure Android PIN must be configured", keyguard.isDeviceSecure)
        device.wakeUp()
        if (keyguard.isKeyguardLocked || keyguard.isDeviceLocked) {
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, height * 4 / 5, width / 2, height / 5, 16)
            // Authenticate the ACTUAL emulator Keyguard with a synthetic CI PIN;
            // never use disable-keyguard, grant trust or bypass OS credentials.
            device.executeShellCommand("input text 246810")
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        }
        val until = SystemClock.elapsedRealtime() + 15_000L
        while ((keyguard.isKeyguardLocked || keyguard.isDeviceLocked) &&
            SystemClock.elapsedRealtime() < until) {
            Thread.sleep(250)
        }
        assertFalse(
            "Secure emulator stayed on the lock screen: cannot run Compose " +
                "or real-OS tests, no authentication bypass allowed",
            keyguard.isKeyguardLocked || keyguard.isDeviceLocked
        )
        device.waitForIdle()
    }
}
