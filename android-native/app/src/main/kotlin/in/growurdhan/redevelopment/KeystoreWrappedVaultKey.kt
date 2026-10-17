package `in`.growurdhan.redevelopment

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * M24 Android-side key custody boundary. An independent random 256-bit DB key
 * is wrapped with a non-exportable Android Keystore AES-GCM key.
 *
 * This class deliberately NEVER opens an Android SQLiteDatabase (plaintext).
 * SQLCipher wiring, migration and recovery are still acceptance blockers.
 * No silent reset on invalidated Keystore key, corrupted envelope or I/O error.
 */
internal class KeystoreWrappedVaultKey(
    context: Context,
    envelopeName: String = "vault-key.v1"
) {
    init {
        require(envelopeName == "vault-key.v1" ||
            envelopeName.matches(Regex("vault-key\\.v1\\.restore-[0-9a-f-]{36}"))) {
            "Unsafe encrypted key-envelope filename"
        }
    }
    // Restore candidates have DISTINCT wrapped database keys and envelope
    // files; only a fully verified candidate envelope may be published
    // exclusively to vault-key.v1. Both use the non-exportable Keystore alias.
    private val keyFile = AtomicFile(File(context.noBackupFilesDir, envelopeName))
    private val alias = "in.growurdhan.redevelopment.vault.wrap.v1"
    private val magic = byteArrayOf(0x47, 0x55, 0x44, 0x4b, 0x45, 0x59, 0x30, 0x31)
    private val random = SecureRandom()

    fun hasExistingEnvelope(): Boolean = keyFile.baseFile.exists() ||
        File(keyFile.baseFile.path + ".bak").exists()

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing != null) return existing as SecretKey
        // Cannot recreate a missing Keystore key if an envelope already exists.
        check(!hasExistingEnvelope()) { "Missing Keystore key for existing vault" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * Caller MUST hold an exclusive vault lifecycle lock. Never call while a
     * restore is pending or after detecting old vault files without this metadata.
     */
    @Synchronized
    fun getOrCreateForFreshInstall(legacyVaultPresent: Boolean): ByteArray {
        if (keyFile.baseFile.exists() || File(keyFile.baseFile.path + ".bak").exists()) return unwrapExisting()
        check(!legacyVaultPresent) { "Refusing to overwrite or orphan legacy vault data" }
        val key = ByteArray(32).also(random::nextBytes)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
            val iv = cipher.iv
            check(iv.size == 12)
            val wrapped = cipher.doFinal(key)
            val envelope = magic + iv + wrapped
            val stream = keyFile.startWrite()
            try {
                stream.write(envelope)
                keyFile.finishWrite(stream)
            } catch (failure: Exception) {
                keyFile.failWrite(stream)
                throw failure
            }
            return key.copyOf()
        } finally {
            key.fill(0)
        }
    }

    @Synchronized
    fun unwrapExisting(): ByteArray {
        // No file = no key; never auto-create on a locked open path.
        val bytes = keyFile.openRead().use { it.readBytes() }
        require(bytes.size == 8 + 12 + 32 + 16 && bytes.copyOfRange(0, 8).contentEquals(magic)) {
            "Invalid vault key envelope"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(),
            GCMParameterSpec(128, bytes.copyOfRange(8, 20)))
        return cipher.doFinal(bytes.copyOfRange(20, bytes.size)).also {
            check(it.size == 32) { "Invalid unwrapped database key length" }
        }
    }
}
