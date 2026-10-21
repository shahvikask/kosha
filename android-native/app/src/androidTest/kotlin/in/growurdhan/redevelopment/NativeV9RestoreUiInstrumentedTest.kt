package `in`.growurdhan.redevelopment

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
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
        compose.onNodeWithText("Approve manual native recovery publication")
            .assertDoesNotExist()
        assertEquals(0, restored)
        assertEquals(0, forensics)
    }

    @Test fun forensicHintSurvivesFreshOwnerCredentialEntryWithoutApproval() {
        var reviews = 0
        compose.setContent {
            GudTheme {
                NativeV9RestoreScreen(true, true, {},
                    { _, _, _ -> null }, { _, _, _, _ -> false },
                    { _, _, _ ->
                        NativeV9RecoveryInspector.Inspection(
                            NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE, 2, true)
                    },
                    resumePreviewAction = { _, _, _ -> reviews++; null },
                    onBack = {})
            }
        }
        fun fill(t: String, v: String) {
            compose.onNodeWithTag(t).performScrollTo().performTextInput(v)
        }
        fill("nativeRecoveryEmail", "x@example.test")
        fill("nativeRecoveryAccountPassword", "sample")
        fill("nativeRecoveryArchivePassword", "long-sample-phrase")
        fill("nativeRecoveryAnswer1", "answer1")
        fill("nativeRecoveryAnswer2", "answer2")
        // PAN/DOB are mandatory parts of the original owner proof, not merely
        // backend checks after the UI has offered an eligible action.
        compose.onNodeWithText("Verify native identity and preview")
            .performScrollTo().assertIsNotEnabled()
        fill("nativeRecoveryPan", "ABCDE1234F")
        fill("nativeRecoveryDob", "1985-01-01")
        compose.onNodeWithTag("nativeRecoveryTermsScroller")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        compose.onNodeWithText("I accept the full versioned Terms")
            .performScrollTo().performClick()
        compose.onNodeWithTag("nativeRecoveryInspectApproval")
            .performScrollTo().performClick()
        compose.onNodeWithText("Inspect interrupted restore (read-only)")
            .performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Reverify native candidate for manual resumption")
            .performScrollTo().assertIsNotEnabled()
        fill("nativeRecoveryAccountPassword", "sample")
        fill("nativeRecoveryArchivePassword", "long-sample-phrase")
        fill("nativeRecoveryAnswer1", "answer1")
        fill("nativeRecoveryAnswer2", "answer2")
        compose.onNodeWithText("Reverify native candidate for manual resumption")
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, reviews)
        compose.onNodeWithText("Approve manual native recovery publication")
            .assertDoesNotExist()
    }
}
