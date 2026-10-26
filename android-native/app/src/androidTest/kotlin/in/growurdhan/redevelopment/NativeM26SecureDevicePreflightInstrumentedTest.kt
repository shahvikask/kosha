package `in`.growurdhan.redevelopment

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fail-closed API35 OS security preflight. Never simulates or bypasses Keyguard. */
@RunWith(AndroidJUnit4::class)
class NativeM26SecureDevicePreflightInstrumentedTest {
    @Test fun actualAndroidDeviceIsSecure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertTrue("Actual Android secure lock credential must be configured", keyguard.isDeviceSecure)
    }
}
