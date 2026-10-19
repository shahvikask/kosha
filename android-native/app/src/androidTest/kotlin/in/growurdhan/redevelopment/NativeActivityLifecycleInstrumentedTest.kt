package `in`.growurdhan.redevelopment

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real ActivityScenario on the API35 emulator, not a mock lifecycle.
 * A real Android credential prompt still requires a secure physical-device test.
 */
@RunWith(AndroidJUnit4::class)
class NativeActivityLifecycleInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun backgroundResumeLeavesAuthenticationScreenLockedAndWindowSecure() {
        compose.onNodeWithText("Verify this device").assertExists()
        compose.onNodeWithText("Private vault unlocked").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            )
        }
        // ActivityScenario issues actual onPause/onStop and onStart/onResume callbacks.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("Verify this device").assertExists()
        compose.onNodeWithText("Private vault unlocked").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            )
        }
    }
}
