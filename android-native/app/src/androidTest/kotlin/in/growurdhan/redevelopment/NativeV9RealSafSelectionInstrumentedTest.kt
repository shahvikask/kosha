package `in`.growurdhan.redevelopment

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android DocumentsUI and Keyguard negative integration: a locally
 * generated malformed native encrypted archive may stage exactly once, but
 * cannot authenticate, publish, replace the staged ciphertext or claim a vault.
 * Positive financial publication and physical two-device security remain OPEN.
 */
@RunWith(AndroidJUnit4::class)
class NativeV9RealSafSelectionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun requireSecureUnlockedHost() {
        NativeV9SecureTestHost.requireUnlockedInteractiveDevice()
    }

    private val instrument by lazy { InstrumentationRegistry.getInstrumentation() }
    private val device by lazy { UiDevice.getInstance(instrument) }
    private val context by lazy { instrument.targetContext }
    private val appPackage by lazy { context.packageName }

    private fun verifyDevice() {
        compose.onNodeWithText("Verify this device").performClick()
        assertTrue("Genuine external Android Keyguard challenge must open",
            device.wait(Until.gone(By.pkg(appPackage)), 20_000))
        assertTrue("Challenge must not be an in-app simulated dialog",
            device.currentPackageName != appPackage)
        // The dedicated emulator uses a synthetic PIN. Never disable Keyguard.
        device.executeShellCommand("input text 246810")
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
    }

    private fun selectRealDocument(filename: String) {
        assertTrue("Real external DocumentsUI must open",
            device.wait(Until.gone(By.pkg(appPackage)), 20_000))
        val entry = device.wait(Until.findObject(By.text(filename)), 10_000)
            ?: run {
                device.findObject(By.desc("Show roots"))?.click()
                device.wait(Until.findObject(By.text("Downloads")), 10_000)?.click()
                device.wait(Until.findObject(By.text(filename)), 15_000)
            }
        assertNotNull("Synthetic Downloads item must be visible in Android DocumentsUI", entry)
        entry!!.click()
        assertTrue("Document picker must return to MainActivity",
            device.wait(Until.hasObject(By.pkg(appPackage)), 20_000))
    }

    private fun openNativePicker() {
        compose.onNodeWithText("Restore an encrypted backup on a new device").performClick()
        compose.onNodeWithText("Restore a newer GUDNAT01 native encrypted backup").performClick()
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup").performClick()
    }

    private fun waitForLocked() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Verify this device").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Confirm one-time native recovery").assertDoesNotExist()
    }

    private fun waitForFreshLogin() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Device verified. Enter your account credentials.")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun realDocumentsUiStagesTamperedNativeAndDeniesPreviewAndReplay() {
        val filename = "gudnat01-invalid-android-ui.kosha"
        val encryptedFake = "GUDNAT01".toByteArray(Charsets.US_ASCII) +
            ByteArray(96) { i -> (i * 3 + 7).toByte() }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Cannot create test-only synthetic Downloads document" }
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(encryptedFake) }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            val pending = File(context.noBackupFilesDir, "restore-pending.v1.kosha")
            val liveKey = File(context.noBackupFilesDir, "vault-key.v1")
            val liveDb = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val claimDirectory = File(context.noBackupFilesDir, "restore-claims")
            assertFalse("Fresh test device requires an unused staging target", pending.exists())
            assertFalse(liveDb.exists())
            assertFalse(liveKey.exists())
            assertFalse(claimDirectory.exists())

            verifyDevice()
            waitForFreshLogin()
            openNativePicker()
            selectRealDocument(filename)
            // Real picker return must revoke the *old* foreground OS admission.
            waitForLocked()
            assertTrue("The exact staged native ciphertext must remain", pending.isFile)
            assertArrayEquals(encryptedFake, pending.readBytes())
            assertFalse(liveKey.exists())
            assertFalse(liveDb.exists())

            // The OS must reauthenticate before any attempt at owner proof.
            verifyDevice()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Encrypted native archive staged")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Encrypted native archive staged").assertIsNotEnabled()
            fun fill(tag: String, value: String) {
                compose.onNodeWithTag(tag).performScrollTo().performTextInput(value)
            }
            fill("nativeRecoveryEmail", "native@example.invalid")
            fill("nativeRecoveryAccountPassword", "Synthetic-login-password-2026!")
            fill("nativeRecoveryArchivePassword", "Synthetic separate archive password")
            fill("nativeRecoveryPan", "ABCDE1234F")
            fill("nativeRecoveryDob", "1985-01-01")
            fill("nativeRecoveryAnswer1", "synthetic city")
            fill("nativeRecoveryAnswer2", "synthetic pet")
            compose.onNodeWithTag("nativeRecoveryTermsScroller")
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
            compose.onNodeWithText("I accept the full versioned Terms")
                .performScrollTo().performClick()
            compose.onNodeWithText("Verify native identity and preview")
                .performScrollTo().performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Native archive or independent owner credentials rejected.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Confirm one-time native recovery").assertDoesNotExist()
            assertArrayEquals("Tampered staging must be immutable", encryptedFake, pending.readBytes())
            assertFalse(liveKey.exists())
            assertFalse(liveDb.exists())
            assertFalse("No claim without authenticated owner proof", claimDirectory.exists())

            compose.activityRule.scenario.recreate()
            waitForLocked()
            assertArrayEquals("Restart must retain encrypted evidence", encryptedFake, pending.readBytes())
            verifyDevice()
            waitForFreshLogin()
            openNativePicker()
            selectRealDocument(filename)
            waitForLocked()
            assertArrayEquals("Second picker cannot overwrite staged evidence",
                encryptedFake, pending.readBytes())
            assertFalse("Replay cannot publish live key", liveKey.exists())
            assertFalse("Replay cannot publish live database", liveDb.exists())
            assertFalse("Replay cannot create a recovery claim", claimDirectory.exists())
        } finally {
            // Delete ONLY our test-created MediaStore item, never the app's
            // immutable pending archive, claim, Terms or recovery candidate.
            resolver.delete(uri, null, null)
            encryptedFake.fill(0)
        }
    }
}
