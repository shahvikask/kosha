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
import androidx.test.uiautomator.UiObject2
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

    // Diagnostic labels are fixed OS navigation names, NEVER user file listings.
    private fun pickerState(): String {
        val labels = listOf("Recent", "Downloads", "Download", "Internal storage",
            "Browse", "Files").filter { device.hasObject(By.text(it)) }
        val pkg = device.currentPackageName.orEmpty()
        return "foreground=" + pkg + ", visibleSystemRoots=" + labels.joinToString("|")
    }

    private fun openRoots(): Boolean {
        val drawer = device.findObject(By.desc("Show roots"))
            ?: device.findObject(By.descContains("navigation drawer"))
            ?: device.findObject(By.descContains("Browse files"))
        if (drawer != null) { drawer.click(); device.waitForIdle(); return true }
        return false
    }

    private fun selectRealDocument(filename: String) {
        // The actual Android DocumentsUI must own the foreground. An in-app
        // simulation is not sufficient even when a matching filename exists.
        val picker = device.wait(Until.findObject(By.pkg("com.google.android.documentsui")), 12_000)
            ?: device.wait(Until.findObject(By.pkg("com.android.documentsui")), 5_000)
        assertNotNull("Android DocumentsUI package not present; " + pickerState(), picker)
        System.out.println("NATIVE_V9_SAF_PICKER_INITIAL " + pickerState())

        fun exact(): UiObject2? = device.findObject(By.text(filename))
        var entry = exact()
        if (entry == null) {
            // DocumentsUI normally starts in Recent; a new test MediaStore item
            // is not guaranteed to appear there. Open the real Downloads root.
            openRoots()
            val downloads = device.wait(Until.findObject(By.text("Downloads")), 5_000)
            if (downloads != null) { downloads.click(); device.waitForIdle() }
            System.out.println("NATIVE_V9_SAF_PICKER_DOWNLOADS " + pickerState())
            entry = device.wait(Until.findObject(By.text(filename)), 6_000)
        }
        if (entry == null) {
            // On some API35 images the Downloads root does not index new
            // MediaStore entries immediately. Browse the external-storage
            // provider's physical Download folder; no direct Uri grant bypass.
            openRoots()
            val storage = device.wait(Until.findObject(By.text("Internal storage")), 5_000)
            if (storage != null) {
                storage.click()
                val folder = device.wait(Until.findObject(By.text("Download")), 5_000)
                    ?: device.findObject(By.text("Downloads"))
                folder?.click()
            }
            System.out.println("NATIVE_V9_SAF_PICKER_STORAGE " + pickerState())
            entry = device.wait(Until.findObject(By.text(filename)), 6_000)
        }
        if (entry == null) {
            // Last resort is DocumentsUI's own search, never injected picker
            // results or direct content-URI delivery to the Activity.
            device.findObject(By.desc("Search"))?.click()
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 4_000)
                ?.setText(filename)
            System.out.println("NATIVE_V9_SAF_PICKER_SEARCH " + pickerState())
            entry = device.wait(Until.findObject(By.text(filename)), 8_000)
        }
        assertNotNull("Exact synthetic file absent from real picker; " + pickerState(), entry)
        val foreground = device.currentPackageName.orEmpty()
        assertTrue("Real Android DocumentsUI must own the selected item: " + foreground,
            foreground != appPackage && foreground.contains("documentsui", ignoreCase = true))
        entry!!.click()
        assertTrue("Real picker must return to MainActivity",
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
            val updated = resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            assertTrue("Synthetic MediaStore publication must complete", updated == 1)
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING,
                MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                assertTrue("Synthetic MediaStore row must be queryable", cursor.moveToFirst())
                assertTrue("Only the exact synthetic fixture may be selected",
                    cursor.getString(0) == filename)
                assertTrue("Synthetic file must be in Downloads",
                    cursor.getString(1).startsWith(Environment.DIRECTORY_DOWNLOADS))
                assertTrue("Synthetic MediaStore item cannot remain pending", cursor.getInt(2) == 0)
                assertTrue("MediaStore size must match the synthetic ciphertext",
                    cursor.getLong(3) == encryptedFake.size.toLong())
                System.out.println("NATIVE_V9_SAF_MEDIASTORE=exact synthetic row visible, pending=0, bounded bytes")
            } ?: error("Synthetic MediaStore row inaccessible")
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
