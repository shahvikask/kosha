package `in`.growurdhan.database

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Genuine frozen src/lib/backup.ts packBackup() binary wire format, NOT a
 * financial snapshot exporter. The caller must first produce a strictly
 * reviewed lossless GUDVAULT v2 JSON snapshot from an authenticated SQLCipher
 * transaction. No JSON is synthesized by this envelope function.
 *
 * Exactly: ASCII GUDVLT01 | big-endian 600000 | salt16 | nonce12 | AES-GCM
 * ciphertext and tag16. Password material matches frozen TypeScript:
 * "GrowUrDhan\n" + normalized email + "\n" + account password.
 */
object LegacyBackupEnvelopeWriter {
    private const val ROUNDS = 600_000
    private const val MAX_PLAINTEXT_BYTES = 64 * 1024 * 1024 - 56
    private val MAGIC = "GUDVLT01".toByteArray(StandardCharsets.US_ASCII)

    /** Pure cryptographic envelope. Does not create or publish a backup file. */
    fun encryptAuthenticatedJson(originalJson: String, email: String,
        password: String): ByteArray {
        val plaintext = originalJson.toByteArray(StandardCharsets.UTF_8)
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES) {
            "Unsupported backup snapshot size"
        }
        require(email.isNotBlank() && password.isNotEmpty())
        require(email.trim().length <= 320)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val material = "GrowUrDhan\n" + email.trim().lowercase(Locale.ROOT) +
            "\n" + password
        val spec = PBEKeySpec(material.toCharArray(), salt, ROUNDS, 256)
        val rawKey = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(rawKey, "AES"),
                GCMParameterSpec(128, nonce))
            val ciphertext = cipher.doFinal(plaintext)
            val buffer = ByteBuffer.allocate(40 + ciphertext.size)
                .order(ByteOrder.BIG_ENDIAN)
            buffer.put(MAGIC)
            buffer.putInt(ROUNDS)
            buffer.put(salt)
            buffer.put(nonce)
            buffer.put(ciphertext)
            return buffer.array()
        } finally {
            rawKey.fill(0)
            plaintext.fill(0)
        }
    }
}
