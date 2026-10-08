package `in`.growurdhan.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generate 256 random bits once; AES-GCM-wrap them under a non-exportable Android
 * Keystore key. The envelope is stored in noBackupFilesDir with atomic replace.
 * Neither login passwords nor the encrypted SQLCipher key are written in plaintext.
 */
internal class VaultKeyEnvelope(
    private val envelope: File,
    private val alias: String,
) {
    private val magic = "GUDKEY02".toByteArray(Charsets.US_ASCII)
    private val atomic = AtomicFile(envelope)
    private val random = SecureRandom()

    fun exists(): Boolean = envelope.exists() || File(envelope.path + ".bak").exists()

    /** The caller MUST wipe the returned bytes once the SQLCipher connection is opened. */
    fun unwrapOrCreate(allowCreate: Boolean): ByteArray {
        if (exists()) return unwrap()
        check(allowCreate) { "No vault key exists. Restore a backup instead of resetting silently." }
        val raw = ByteArray(32).also(random::nextBytes)
        try {
            val key = masterKey(create = true)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            val encrypted = cipher.doFinal(raw)
            val buffer = ByteArrayOutputStream(8 + 1 + 12 + 4 + encrypted.size)
            DataOutputStream(buffer).use { out ->
                out.write(magic)
                out.writeByte(1)
                out.write(cipher.iv)
                out.writeInt(encrypted.size)
                out.write(encrypted)
            }
            val stream = atomic.startWrite()
            try {
                stream.write(buffer.toByteArray())
                atomic.finishWrite(stream)
            } catch (failure: Exception) {
                atomic.failWrite(stream)
                throw failure
            }
            return raw
        } catch (failure: Exception) {
            raw.fill(0)
            throw VaultUnavailable("Could not create a device-bound vault key.", failure)
        }
    }

    private fun unwrap(): ByteArray = try {
        val encoded = atomic.openRead().use { input ->
            input.readNBytes(512).also { bytes ->
                require(bytes.size <= 128) { "Vault key envelope is oversized." }
            }
        }
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            val header = ByteArray(8)
            input.readFully(header)
            require(header.contentEquals(magic)) { "Unrecognized vault key envelope." }
            require(input.readUnsignedByte() == 1) { "Unsupported vault key format." }
            val nonce = ByteArray(12).also(input::readFully)
            val size = input.readInt()
            require(size == 48 && input.available() == size) { "Invalid key ciphertext length." }
            val ciphertext = ByteArray(size).also(input::readFully)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, masterKey(create = false), GCMParameterSpec(128, nonce))
                doFinal(ciphertext).also { require(it.size == 32) }
            }
        }
    } catch (failure: Exception) {
        throw VaultUnavailable("Vault key cannot be unlocked on this device. Do not delete the vault; use a recovery backup.", failure)
    }

    private fun masterKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing != null) return existing as? SecretKey
            ?: throw VaultUnavailable("Unexpected Android Keystore entry type.")
        if (!create) throw VaultUnavailable("Device Keystore key is missing; encrypted data cannot be opened.")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }
}

/** Sanitized public error. Internal causes are retained only in memory, not logged. */
class VaultUnavailable(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
