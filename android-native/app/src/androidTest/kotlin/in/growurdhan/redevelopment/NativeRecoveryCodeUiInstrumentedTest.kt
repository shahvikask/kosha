package `in`.growurdhan.redevelopment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI callbacks: this tests screen exposure, not Android Keyguard authentication. */
@RunWith(AndroidJUnit4::class)
class NativeRecoveryCodeUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneTimeCodeHidesOtherActionsUntilAcknowledgedAndLocks() {
        var admitted by mutableStateOf(true)
        var unlocked by mutableStateOf(true)
        var locked = false
        compose.setContent {
            GudTheme {
                NativeAuthenticationScreen(
                    deviceVerified = admitted,
                    unlocked = unlocked,
                    deviceMessage = "",
                    onDeviceChallenge = { admitted = true },
                    onEnroll = { _, _, _ -> null },
                    onLogin = { _, _, _ -> NativeAccountRepository.SignInResult.DENIED },
                    onRotation = { _, _, _ -> false },
                    onRecover = { _, _, _, _, _, _, _ -> false },
                    onReplaceRecoveryCode = { "GUD-SYNTHETIC-TEST-ONLY" },
                    onLock = { locked = true; admitted = false; unlocked = false },
                    onOpenBooks = {},
                    onDeclineTerms = {}
                )
            }
        }
        compose.onNodeWithText("Replace saved recovery code").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("GUD-SYNTHETIC-TEST-ONLY").assertExists()
        compose.onNodeWithText("Replace saved recovery code").assertDoesNotExist()
        compose.onNodeWithText("Lock now").assertDoesNotExist()
        compose.onNodeWithText("Unlock private vault").assertDoesNotExist()
        compose.onNodeWithText("I have saved the recovery code").performClick()
        compose.waitForIdle()
        assert(locked)
        compose.onNodeWithText("GUD-SYNTHETIC-TEST-ONLY").assertDoesNotExist()
        compose.onNodeWithText("Verify this device").assertExists()
    }
}
