package `in`.growurdhan.data.vault

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable backup v1. Authenticated AES-256-GCM ciphertext, PBKDF2 600,000 rounds. */
internal object VaultBackupCodec {
    private val MAGIC = "GUDNBACK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 600_000
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    // Bounded in-memory M2 exporter. Larger streaming backup format requires an explicit version bump.
    const val MAX_PLAINTEXT = 32 * 1024 * 1024
    private const val TAG_SIZE = 16
    private const val HEADER_SIZE = 8 + 1 + 4 + SALT_SIZE + NONCE_SIZE + 4

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Backup password must contain at least 12 characters." }
        require(plain.isNotEmpty() && plain.size <= MAX_PLAINTEXT) { "Unsupported backup size." }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).putInt(ITERATIONS).put(salt).put(nonce)
            .putInt(plain.size + TAG_SIZE).array()
        val key = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plain)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        require(bytes.size >= HEADER_SIZE + TAG_SIZE && bytes.size <= HEADER_SIZE + MAX_PLAINTEXT + TAG_SIZE) {
            "Unsupported backup size."
        }
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size).also(input::get)
        require(magic.contentEquals(MAGIC)) { "Invalid GrowUrDhan backup header." }
        require(input.get() == VERSION) { "Unsupported backup format version." }
        require(input.int == ITERATIONS) { "Unsupported backup key derivation policy." }
        val salt = ByteArray(SALT_SIZE).also(input::get)
        val nonce = ByteArray(NONCE_SIZE).also(input::get)
        val length = input.int
        require(length >= TAG_SIZE && length == input.remaining()) { "Invalid backup ciphertext length." }
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val key = deriveKey(passphrase, salt)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
                doFinal(bytes, HEADER_SIZE, length)
            }
        } catch (failure: AEADBadTagException) {
            throw VaultUnavailable("Backup password is wrong or backup authentication failed.", failure)
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
