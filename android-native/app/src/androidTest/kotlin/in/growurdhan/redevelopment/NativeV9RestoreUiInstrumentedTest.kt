package `in`.growurdhan.redevelopment

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose callback guards, not a substitute for Android Keyguard/SQLCipher tests. */
@RunWith(AndroidJUnit4::class)
class NativeV9RestoreUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeFileAndForensicSelectionRequireDeviceAdmission() {
        var pick = 0
        var preview = 0
        var forensic = 0
        compose.setContent {
            GudTheme {
                NativeV9RestoreScreen(
                    deviceVerified = false, staged = false,
                    selectEncryptedFile = { pick++ },
                    previewAction = { _, _, _ -> preview++; null },
                    publishAction = { _, _, _, _ -> false },
                    forensicAction = { _, _, _ -> forensic++; null },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup")
            .assertExists().assertIsNotEnabled()
        assertEquals(0, pick)
        assertEquals(0, preview)
        assertEquals(0, forensic)
    }

    @Test fun nativeRestoreAndForensicActionsCannotBypassOwnerProofOrTerms() {
        var restored = 0
        var forensics = 0
        compose.setContent {
            GudTheme {
                NativeV9RestoreScreen(
                    deviceVerified = true, staged = true,
                    selectEncryptedFile = {},
                    previewAction = { _, _, _ -> null },
                    publishAction = { _, _, _, _ -> restored++; true },
                    forensicAction = { _, _, _ -> forensics++; null },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Verify native identity and preview")
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Inspect interrupted restore (read-only)")
            .performScrollTo().assertIsNotEnabled()
        assertEquals(0, restored)
        assertEquals(0, forensics)
    }
}
