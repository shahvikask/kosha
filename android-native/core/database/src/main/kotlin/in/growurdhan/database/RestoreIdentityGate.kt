package `in`.growurdhan.database

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure, fail-closed credential verification for a decrypted frozen backup account.
 * This is a necessary but NOT sufficient restore approval: device trust, target
 * ownership, durable replay prevention and an atomic SQLCipher transaction
 * are additional gates. No account credentials are persisted here.
 */
object RestoreIdentityGate {
    // Frozen auth.ts hashSecret() uses 210_000, while backup.ts/vault.ts
    // ENVELOPE encryption uses 600_000. Account hashes store their own
    // iteration count and must not be confused with envelope KDF params.
    // Bounded historical counts are accepted only for read-only inspection.
    private const val MIN_ACCOUNT_ROUNDS = 100_000
    private const val MAX_ACCOUNT_ROUNDS = 1_000_000
    data class Account(
        val email: String, val passwordSalt: String, val passwordHash: String,
        val passwordIterations: Int, val panForStatements: String?, val panMasked: String?,
        val dateOfBirth: String?, val q1Id: String, val q1Hash: String,
        val q2Id: String, val q2Hash: String
    )
    data class Credentials(
        val email: String, val password: String, val pan: String,
        val dateOfBirth: String, val q1Id: String, val q1Answer: String,
        val q2Id: String, val q2Answer: String
    )

    /** Throws without returning any personally identifying value on failure. */
    fun verifyForNewDevice(account: Account, input: Credentials) {
        require(input.password.length <= 4096 && input.q1Answer.length <= 4096 &&
            input.q2Answer.length <= 4096) { "Restore identity invalid" }
        require(account.email.trim().lowercase(Locale.ROOT) ==
            input.email.trim().lowercase(Locale.ROOT)) { "Restore identity invalid" }
        require(verifySecret(input.password, account.passwordSalt,
            account.passwordHash, account.passwordIterations)) { "Restore identity invalid" }
        val pan = input.pan.trim().uppercase(Locale.ROOT)
        require(Regex("[A-Z]{5}[0-9]{4}[A-Z]").matches(pan)) { "Restore identity invalid" }
        val storedFull = account.panForStatements?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val storedMasked = account.panMasked?.trim()?.uppercase(Locale.ROOT).orEmpty()
        require(when {
            storedFull.isNotEmpty() -> safeEquals(pan, storedFull)
            storedMasked.isNotEmpty() -> safeEquals(pan.take(5) + "****" + pan.last(), storedMasked)
            else -> false
        }) { "Restore identity invalid" }
        require(input.dateOfBirth.isNotBlank() &&
            safeEquals(input.dateOfBirth, account.dateOfBirth.orEmpty())) {
            "Restore identity invalid"
        }
        require(input.q1Id.isNotBlank() && input.q2Id.isNotBlank() &&
            input.q1Id != input.q2Id && input.q1Id == account.q1Id &&
            input.q2Id == account.q2Id) { "Restore identity invalid" }
        require(verifyPacked(normalizeAnswer(input.q1Answer), account.q1Hash) &&
            verifyPacked(normalizeAnswer(input.q2Answer), account.q2Hash)) {
            "Restore identity invalid"
        }
    }

    private fun normalizeAnswer(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun verifyPacked(answer: String, packed: String): Boolean {
        val parts = packed.split(':')
        if (parts.size != 3) return false
        val iterations = parts[2].toIntOrNull() ?: return false
        return verifySecret(answer, parts[0], parts[1], iterations)
    }

    private fun verifySecret(secret: String, saltB64: String, hashB64: String,
                             iterations: Int): Boolean {
        if (iterations !in MIN_ACCOUNT_ROUNDS..MAX_ACCOUNT_ROUNDS ||
            saltB64.length > 32 || hashB64.length > 48) return false
        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = Base64.getDecoder().decode(saltB64)
            expected = Base64.getDecoder().decode(hashB64)
        } catch (_: IllegalArgumentException) { return false }
        if (salt.size != 16 || expected.size != 32) return false
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        val actual = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return try { MessageDigest.isEqual(actual, expected) }
        finally { actual.fill(0) }
    }

    private fun safeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
