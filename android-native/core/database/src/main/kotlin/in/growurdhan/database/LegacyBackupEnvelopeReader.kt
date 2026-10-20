package `in`.growurdhan.database

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Independent JVM/JCE reader of the PINNED original exporter envelopes.
 * Outputs decrypted JSON verbatim; does NOT approve account identity, owners,
 * imported financial events, replay, or a production restore.
 */
object LegacyBackupEnvelopeReader {
    private const val ITERATIONS = 600_000
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private val MAGIC = "GUDVLT01".toByteArray(StandardCharsets.US_ASCII)

    data class Kosha1(
        val magic: String, val kdf: String, val iterations: Int,
        val saltB64: String, val ivB64: String, val cipherB64: String
    )

    fun decodeGUDVLT01(bytes: ByteArray, email: String, password: String): String {
        require(bytes.size in 56..MAX_BACKUP_BYTES) { "Unsupported encrypted backup size" }
        require(bytes.copyOfRange(0, 8).contentEquals(MAGIC)) { "Unsupported backup magic" }
        val rounds = ByteBuffer.wrap(bytes, 8, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        require(rounds == ITERATIONS) { "Unsupported backup KDF parameters" }
        val material = "GrowUrDhan\n" + email.trim().lowercase(Locale.ROOT) + "\n" + password
        return decrypt(material, bytes.copyOfRange(12, 28),
            bytes.copyOfRange(28, 40), bytes.copyOfRange(40, bytes.size))
    }

    fun decodeKOSHA1(blob: Kosha1, password: String): String {
        // The immutable frozen TypeScript unpackLegacyJson() accepts absent
        // KDF metadata and missing/zero rounds using PBKDF2-SHA256/600000.
        // An explicitly different algorithm or nonzero round count is never
        // silently downgraded. GCM authentication is still mandatory.
        require(blob.magic == "KOSHA1" &&
            (blob.kdf.isEmpty() || blob.kdf == "PBKDF2-SHA256") &&
            (blob.iterations == 0 || blob.iterations == ITERATIONS)) {
            "Unsupported legacy backup parameters"
        }
        // Reject oversized wire strings BEFORE allocating decoded ciphertext.
        // A malformed KOSHA1 document must not force an unbounded Base64 copy.
        require(blob.saltB64.length <= 32 && blob.ivB64.length <= 24 &&
            blob.cipherB64.length <= ((MAX_BACKUP_BYTES + 2L) / 3L * 4L + 4L)) {
            "Oversized legacy Base64 envelope"
        }
        val decoder = java.util.Base64.getDecoder()
        val salt = decoder.decode(blob.saltB64)
        val nonce = decoder.decode(blob.ivB64)
        val encrypted = decoder.decode(blob.cipherB64)
        require(encrypted.size <= MAX_BACKUP_BYTES) { "Oversized backup" }
        return decrypt(password, salt, nonce, encrypted)
    }

    private fun decrypt(material: String, salt: ByteArray, nonce: ByteArray,
                        encrypted: ByteArray): String {
        require(salt.size == 16 && nonce.size == 12 && encrypted.size >= 16) {
            "Unsupported backup envelope lengths"
        }
        val passwordSpec = PBEKeySpec(material.toCharArray(), salt, ITERATIONS, 256)
        val rawKey = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(passwordSpec).encoded
        } finally { passwordSpec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rawKey, "AES"),
                GCMParameterSpec(128, nonce))
            // GCM checks authentication before yielding plaintext, but a
            // successfully authenticated byte sequence need not be valid
            // UTF-8 JSON. Never substitute U+FFFD and silently alter financial
            // identifiers, values, or the source JSON digest.
            val plain = cipher.doFinal(encrypted)
            try {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(plain)).toString()
            } finally { plain.fill(0) }
        } finally { rawKey.fill(0) }
    }
}
