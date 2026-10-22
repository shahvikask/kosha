package `in`.growurdhan.redevelopment

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
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
import java.security.MessageDigest
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fully synthetic authenticated native backup through REAL MainActivity,
 * Android credential challenge, DocumentsUI, independent original owner
 * credentials/current Terms/immutable preview and explicit publication.
 *
 * Run ONLY on a NEW ephemeral API35 app installation. Previous negative SAF
 * tests deliberately preserve staged evidence and must never be cleaned up.
 * Not physical cross-phone/device hardware acceptance.
 */
@RunWith(AndroidJUnit4::class)
class NativeV9RealPositiveRestoreInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun secureHost() { NativeV9SecureTestHost.requireUnlockedInteractiveDevice() }
    private val instrument by lazy { InstrumentationRegistry.getInstrumentation() }
    private val context by lazy { instrument.targetContext }
    private val device by lazy { UiDevice.getInstance(instrument) }
    private val appPackage by lazy { context.packageName }

    private fun reauthenticate() {
        compose.onNodeWithText("Verify this device").performClick()
        assertTrue("Real OS credential challenge required",
            device.wait(Until.gone(By.pkg(appPackage)), 20_000))
        assertTrue("Credential prompt must be external to GrowUrDhan",
            device.currentPackageName != appPackage)
        // Wait for the genuine external credential activity to settle before
        // entering the synthetic CI PIN. Sending input at the first window
        // transition can race the PIN field becoming interactive.
        device.waitForIdle()
        assertTrue("PIN must be entered in the external Android credential UI",
            device.currentPackageName != appPackage)
        device.executeShellCommand("input text 246810")
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        // Do NOT call scenario.moveToState(CREATED) after OS confirmation:
        // MainActivity.onStop() intentionally revokes a successful admission.
        // Only the real ActivityResult callback may set deviceVerified.
        assertTrue("Credential UI must return to the real MainActivity",
            device.wait(Until.hasObject(By.pkg(appPackage)), 20_000))
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Device verified. Enter your account credentials.")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pickerSelect(filename: String) {
        val shown = device.wait(Until.findObject(By.pkg("com.google.android.documentsui")), 18_000)
            ?: device.wait(Until.findObject(By.pkg("com.android.documentsui")), 5_000)
        assertNotNull("A real Android DocumentsUI must own the file selection", shown)
        val item = device.wait(Until.findObject(By.text(filename)), 15_000)
        assertNotNull("Only our exact synthetic native file may be selected", item)
        assertTrue("Picker must be external Android DocumentsUI",
            device.currentPackageName.orEmpty().contains("documentsui", ignoreCase = true))
        item!!.click()
        assertTrue("Real picker must return to GrowUrDhan",
            device.wait(Until.hasObject(By.pkg(appPackage)), 20_000))
    }

    private fun credentials() {
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
        compose.onNodeWithText("End of current version reached").assertExists()
        compose.onNodeWithText("I accept the full versioned Terms")
            .performScrollTo().performClick()
    }

    private fun sha(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test fun realDocumentsUiOwnerApprovedNativePublicationAndReplayDenial() {
        // Reuses the existing proven native test fixture: valid 210k-iteration
        // salted owner proof, typed financial rows, snapshot and Terms metadata.
        val source = NativeV9ArchiveInstrumentedTest().syntheticAuthenticatedArchive()
        val filename = "gudnat01-verified-native-ui.kosha"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri: Uri = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Synthetic Downloads fixture cannot be created" }
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(source) }
            assertEquals(1, resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null))
            val pending = File(context.noBackupFilesDir, "restore-pending.v1.kosha")
            val key = File(context.noBackupFilesDir, "vault-key.v1")
            val target = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val claims = File(context.noBackupFilesDir, "restore-claims")
            assertFalse(pending.exists()); assertFalse(key.exists())
            assertFalse(target.exists()); assertFalse(claims.exists())

            reauthenticate()
            compose.onNodeWithText("Restore an encrypted backup on a new device").performClick()
            compose.onNodeWithText("Restore a newer GUDNAT01 native encrypted backup").performClick()
            compose.onNodeWithText("Choose native encrypted GUDNAT01 backup").performClick()
            pickerSelect(filename)

            // Returning through the picker revokes old OS admission.
            compose.waitUntil(25_000) {
                compose.onAllNodesWithText("Verify this device")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertArrayEquals("Only original ciphertext can be staged", source, pending.readBytes())
            assertFalse(key.exists()); assertFalse(target.exists()); assertFalse(claims.exists())

            reauthenticate()
            compose.waitUntil(25_000) {
                compose.onAllNodesWithText("Encrypted native archive staged")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            credentials()
            compose.onNodeWithText("Verify native identity and preview")
                .performScrollTo().performClick()
            compose.waitUntil(110_000) {
                compose.onAllNodesWithText("Authenticated financial snapshot:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertFalse("Read-only preview cannot publish a key", key.exists())
            assertFalse("Read-only preview cannot publish database", target.exists())
            assertFalse("Read-only preview cannot claim a source", claims.exists())

            compose.onNodeWithTag("nativeRecoveryPublishApproval")
                .performScrollTo().performClick()
            compose.onNodeWithText("Confirm one-time native recovery")
                .performScrollTo().performClick()

            compose.waitUntil(120_000) { target.isFile && key.isFile }
            assertTrue("Vault publication must be nonempty", target.length() > 0)
            assertTrue("Wrapped key must be persistent", key.length() > 0)
            assertTrue("One-use claim must be durable", claims.isDirectory)
            assertArrayEquals("Authenticated staged source remains immutable",
                source, pending.readBytes())
            assertFalse("Ciphertext database cannot be plaintext SQLite",
                target.readBytes().take(16).toByteArray()
                    .contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))

            val secret = KeystoreWrappedVaultKey(context).unwrapExisting()
            try {
                System.loadLibrary("sqlcipher")
                val preserve = DatabaseErrorHandler { _, _ ->
                    throw AssertionError("Encrypted restored candidate damaged")
                }
                SQLiteDatabase.openDatabase(target.absolutePath, secret, null,
                    SQLiteDatabase.OPEN_READONLY, preserve, null).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM holding", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue("Authenticated synthetic holding must survive", cursor.getLong(0) > 0)
                    }
                }
            } finally { secret.fill(0) }

            val beforeKey = sha(key.readBytes())
            val beforeDb = sha(target.readBytes())
            assertThrows(IllegalStateException::class.java) {
                NativeV9NewDeviceRestore(context).stageNativeOnce(source.copyOf())
            }
            assertArrayEquals(beforeKey, sha(key.readBytes()))
            assertArrayEquals(beforeDb, sha(target.readBytes()))
            assertArrayEquals(source, pending.readBytes())
        } finally {
            // Delete ONLY our MediaStore test item, never claims/key/ciphertext
            // or any permanent staged/archive forensic evidence.
            resolver.delete(uri, null, null)
            source.fill(0)
        }
    }
}
