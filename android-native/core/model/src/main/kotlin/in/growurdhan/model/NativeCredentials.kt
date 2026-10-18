package `in`.growurdhan.model

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Frozen auth.ts-compatible PBKDF2 hashes. This is authentication only, not a SQLCipher key. */
object NativeCredentials {
    const val ROUNDS = 210_000
    private val questions = setOf("maiden", "school", "pet", "city", "teacher", "vehicle")
    private val random = SecureRandom()
    data class Enrollment(val email: String, val firstName: String, val lastName: String,
        val password: String, val firstQuestion: String, val firstAnswer: String,
        val secondQuestion: String, val secondAnswer: String)
    data class Account(val email: String, val firstName: String, val lastName: String,
        val passwordSalt: String, val passwordHash: String, val passwordIterations: Int,
        val q1Id: String, val q1Hash: String, val q2Id: String, val q2Hash: String,
        val passwordChangedAt: String, val createdAt: String)
    data class Hash(val salt: String, val hash: String, val iterations: Int) {
        fun packed(): String = "$salt:$hash:$iterations"
    }

    fun enroll(input: Enrollment, createdAt: String): Account {
        Instant.parse(createdAt)
        val email = input.email.trim().lowercase(Locale.ROOT)
        require(email.length <= 254 && Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
            .matches(email)) { "Invalid account registration" }
        val first = input.firstName.trim().replace(Regex("\\s+"), " ")
        val last = input.lastName.trim().replace(Regex("\\s+"), " ")
        require(first.isNotEmpty() && last.isNotEmpty() && first.length <= 128 &&
            last.length <= 128) { "Invalid account registration" }
        require(input.password.length in 8..4096 &&
            input.password.any(Char::isUpperCase) && input.password.any(Char::isLowerCase) &&
            input.password.any(Char::isDigit) &&
            input.password.any { !it.isLetterOrDigit() }) { "Invalid account registration" }
        require(input.firstQuestion in questions && input.secondQuestion in questions &&
            input.firstQuestion != input.secondQuestion &&
            input.firstAnswer.length <= 4096 && input.secondAnswer.length <= 4096 &&
            normalize(input.firstAnswer).isNotEmpty() &&
            normalize(input.secondAnswer).isNotEmpty()) { "Invalid account registration" }
        val password = hash(input.password)
        val q1 = hash(normalize(input.firstAnswer))
        val q2 = hash(normalize(input.secondAnswer))
        return Account(email, first, last, password.salt, password.hash, password.iterations,
            input.firstQuestion, q1.packed(), input.secondQuestion, q2.packed(), createdAt, createdAt)
    }

    fun verifyPassword(record: Account, email: String, password: String): Boolean =
        email.length <= 254 && password.length <= 4096 &&
        constantEquals(record.email.lowercase(Locale.ROOT), email.trim().lowercase(Locale.ROOT)) &&
        verifyHash(password, record.passwordSalt, record.passwordHash, record.passwordIterations)

    fun verifyRecovery(record: Account, q1Id: String, answer1: String,
        q2Id: String, answer2: String): Boolean =
        q1Id == record.q1Id && q2Id == record.q2Id && q1Id != q2Id &&
        answer1.length <= 4096 && answer2.length <= 4096 &&
        verifyPacked(normalize(answer1), record.q1Hash) &&
        verifyPacked(normalize(answer2), record.q2Hash)

    /** Old-password rotation only. Forgotten-password storage flow is a separate M25 gate. */
    fun changePassword(record: Account, newPassword: String, changedAt: String,
        verifiedOldPassword: String): Account {
        require(verifyPassword(record, record.email, verifiedOldPassword)) { "Invalid account credentials" }
        require(newPassword.length in 8..4096 && newPassword.any(Char::isUpperCase) &&
            newPassword.any(Char::isLowerCase) && newPassword.any(Char::isDigit) &&
            newPassword.any { !it.isLetterOrDigit() }) { "Invalid new password" }
        Instant.parse(changedAt)
        val replacement = hash(newPassword)
        return record.copy(passwordSalt = replacement.salt, passwordHash = replacement.hash,
            passwordIterations = replacement.iterations, passwordChangedAt = changedAt)
    }

    private fun hash(secret: String): Hash {
        val salt = ByteArray(16).also(random::nextBytes)
        return try {
            val digest = derive(secret, salt, ROUNDS)
            try { Hash(Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(digest), ROUNDS) }
            finally { digest.fill(0) }
        } finally { salt.fill(0) }
    }
    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun verifyPacked(secret: String, packed: String): Boolean {
        val parts = packed.split(':')
        return parts.size == 3 && verifyHash(secret, parts[0], parts[1],
            parts[2].toIntOrNull() ?: return false)
    }
    private fun verifyHash(secret: String, saltText: String, expectedText: String,
        rounds: Int): Boolean {
        if (rounds !in 100_000..1_000_000 || saltText.length > 32 ||
            expectedText.length > 48 || secret.length > 4096) return false
        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = Base64.getDecoder().decode(saltText)
            expected = Base64.getDecoder().decode(expectedText)
        } catch (_: IllegalArgumentException) { return false }
        if (salt.size != 16 || expected.size != 32) return false
        val actual = derive(secret, salt, rounds)
        return try { MessageDigest.isEqual(actual, expected) }
        finally { actual.fill(0); salt.fill(0); expected.fill(0) }
    }
    private fun normalize(answer: String): String =
        answer.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun constantEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/** In-memory only. Unlock requires password verification; backgrounding must call lock(). */
class NativeSession(private val idleMillis: Long = 300_000L) {
    init { require(idleMillis > 0) }
    private var identity: String? = null
    private var lastUse: Long = 0L
    private var failures = 0
    @Synchronized fun login(account: NativeCredentials.Account, email: String,
                            password: String, nowElapsedMillis: Long): Boolean {
        lock()
        if (failures >= 5) return false
        if (!NativeCredentials.verifyPassword(account, email, password)) {
            failures++
            return false
        }
        failures = 0
        identity = account.email
        lastUse = nowElapsedMillis
        return true
    }
    @Synchronized fun allowed(email: String, nowElapsedMillis: Long): Boolean {
        if (identity == null || identity != email || nowElapsedMillis < lastUse ||
            nowElapsedMillis - lastUse >= idleMillis) { lock(); return false }
        lastUse = nowElapsedMillis
        return true
    }
    @Synchronized fun lock() { identity = null; lastUse = 0L }
    @Synchronized fun onBackground() = lock()
    @Synchronized fun blocked(): Boolean = failures >= 5
}