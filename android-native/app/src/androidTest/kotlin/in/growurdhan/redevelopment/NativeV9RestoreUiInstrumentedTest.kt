package `in`.growurdhan.redevelopment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /**
     * Complete Compose callback choreography for native source selection ->
     * revoked session -> fresh device admission -> owner proof/Terms ->
     * authenticated preview -> separate owner approval -> publication.
     * SAF and Keyguard are synthetic callbacks here; real OS UI is NOT tested.
     */
    @Test fun nativeFileAndForensicSelectionRequireDeviceAdmission() {
        var deviceAdmitted by mutableStateOf(false)
        var staged by mutableStateOf(false)
        var selected = 0
        var previews = 0
        var published = 0
        var forensic = 0
        compose.setContent {
            GudTheme {
                NativeV9RestoreScreen(
                    deviceVerified = deviceAdmitted, staged = staged,
                    selectEncryptedFile = {
                        selected++
                        staged = true
                        deviceAdmitted = false // SAF leaves the Activity; OS admission expires.
                    },
                    previewAction = { _, _, _ ->
                        previews++
                        NativeV9NewDeviceRestore.Review(
                            "a".repeat(64), "b".repeat(64), 2, false)
                    },
                    publishAction = { _, _, _, review ->
                        assertEquals(true, review.ownerApproved)
                        published++
                        true
                    },
                    forensicAction = { _, _, _ -> forensic++; null },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup")
            .assertExists().assertIsNotEnabled()
        assertEquals(0, selected)
        assertEquals(0, previews)
        compose.runOnUiThread { deviceAdmitted = true }
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup")
            .performClick()
        compose.waitForIdle()
        assertEquals(1, selected)
        // The just-returned (simulated) SAF picker cannot inherit admission.
        compose.onNodeWithText("Encrypted native archive staged").assertIsNotEnabled()
        compose.onNodeWithText("Verify native identity and preview").assertDoesNotExist()
        assertEquals(0, previews)
        compose.runOnUiThread { deviceAdmitted = true } // simulated fresh OS challenge
        fun fill(t: String, v: String) {
            compose.onNodeWithTag(t).performScrollTo().performTextInput(v)
        }
        fill("nativeRecoveryEmail", "x@example.test")
        fill("nativeRecoveryAccountPassword", "sample")
        fill("nativeRecoveryArchivePassword", "long-sample-phrase")
        fill("nativeRecoveryPan", "ABCDE1234F")
        fill("nativeRecoveryDob", "1985-01-01")
        fill("nativeRecoveryAnswer1", "answer1")
        fill("nativeRecoveryAnswer2", "answer2")
        compose.onNodeWithText("Verify native identity and preview")
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("nativeRecoveryTermsScroller")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        compose.onNodeWithText("I accept the full versioned Terms")
            .performScrollTo().performClick()
        compose.onNodeWithText("Verify native identity and preview")
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, previews)
        compose.onNodeWithText("Confirm one-time native recovery")
            .performScrollTo().assertIsNotEnabled()
        assertEquals(0, published)
        compose.onNodeWithTag("nativeRecoveryPublishApproval")
            .performScrollTo().performClick()
        compose.onNodeWithText("Confirm one-time native recovery")
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, published)
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
