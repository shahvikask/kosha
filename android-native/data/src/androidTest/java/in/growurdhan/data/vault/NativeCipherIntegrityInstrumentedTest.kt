package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic, isolated encrypted file only. Does not use real family data. */
@RunWith(AndroidJUnit4::class)
class NativeCipherIntegrityInstrumentedTest {
    private lateinit var root: File
    private lateinit var alias: String
    private lateinit var vault: SqlCipherVaultRepository

    @Before fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        root = File(context.noBackupFilesDir, "cipher-hmac-test-$suffix")
        alias = "in.growurdhan.cipher.hmac.test.$suffix"
        vault = SqlCipherVaultRepository(context, root, alias)
    }

    @After fun tearDown(): Unit = runBlocking {
        vault.close()
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun tamperedEncryptedPageFailsClosedWithoutDeletingTheVault(): Unit = runBlocking {
        vault.openOrCreate()
        vault.registerFamilyMember("Synthetic Primary", "Self")
        vault.close()

        val dbFile = File(root, "wealth.db")
        val keyFile = File(root, "wrapped.key")
        assertTrue(dbFile.isFile && dbFile.length() >= 4096L)
        assertTrue(keyFile.isFile)

        // Flip one bit near the end of an encrypted page, after SQLite has
        // been closed and its checkpoint is complete.
        RandomAccessFile(dbFile, "rw").use { file ->
            val offset = file.length() - 32L
            file.seek(offset)
            val original = file.readUnsignedByte()
            file.seek(offset)
            file.writeByte(original xor 0x01)
        }
        val tamperedSha256 = MessageDigest.getInstance("SHA-256").digest(dbFile.readBytes())

        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.openOrCreate() }
        }
        assertFalse(vault.status().opened)
        assertTrue(keyFile.isFile)
        assertArrayEquals(
            tamperedSha256,
            MessageDigest.getInstance("SHA-256").digest(dbFile.readBytes()),
        )
    }
}
