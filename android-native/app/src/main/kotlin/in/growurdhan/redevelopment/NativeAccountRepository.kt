package `in`.growurdhan.redevelopment

import android.content.Context
import android.os.SystemClock
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeSession
import java.time.Instant
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Explicit enrollment/login boundary. UI remains locked until a separate M25 UI flow is approved. */
internal class NativeAccountRepository(
    context: Context, private val deviceAdmission: NativeDeviceAdmission,
    private val wallClockMillis: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val THROTTLE_KEY = "m25.auth.failure_state"
        private const val RECOVERY_KEY = "m25.auth.recovery_code_verifier"
        private const val COOLDOWN_MILLIS = 30L * 60L * 1000L
    }
    private data class Throttle(val failures: Int, val lastSeen: Long, val blockedUntil: Long)
    private fun readThrottle(db: SQLiteDatabase): Throttle {
        val encoded = readSetting(db, THROTTLE_KEY) ?: return Throttle(0, 0, 0)
        val parts = encoded.split(':')
        require(parts.size == 3) { "Invalid encrypted throttle" }
        val failures = parts[0].toIntOrNull()
        val lastSeen = parts[1].toLongOrNull()
        val blocked = parts[2].toLongOrNull()
        require(failures != null && failures in 0..5 && lastSeen != null && lastSeen >= 0 &&
            blocked != null && blocked >= 0) { "Invalid encrypted throttle" }
        return Throttle(failures, lastSeen, blocked)
    }
    private fun readSetting(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    private fun writeSetting(db: SQLiteDatabase, key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
            arrayOf<Any>(key, value))
    }
    private fun saveThrottle(db: SQLiteDatabase, state: Throttle) =
        writeSetting(db, THROTTLE_KEY,
            "${state.failures}:${state.lastSeen}:${state.blockedUntil}")
    private fun admitted(previous: Throttle, now: Long): Throttle? {
        if (now <= 0L || now < previous.lastSeen ||
            (previous.blockedUntil > 0L && now < previous.blockedUntil)) return null
        return if (previous.blockedUntil > 0L && now >= previous.blockedUntil)
            Throttle(0, now, 0) else previous.copy(lastSeen = now)
    }
    private fun denied(previous: Throttle, now: Long): Throttle {
        val count = (previous.failures + 1).coerceAtMost(5)
        val until = if (count == 5) {
            if (now > Long.MAX_VALUE - COOLDOWN_MILLIS) Long.MAX_VALUE
            else now + COOLDOWN_MILLIS
        } else 0L
        return Throttle(count, now, until)
    }
    enum class SignInResult { UNLOCKED, DENIED, ROTATION_REQUIRED }
    private val vault = EncryptedVaultStore(context)
    private var session = NativeSession()
    // Bound to the exact encrypted account credential version at successful login.
    private var sessionPasswordHash: String? = null
    private fun ticket(): Long = deviceAdmission.ticket()
        ?: throw IllegalStateException("Android device authentication required")
    private fun requireTicket(ticket: Long) {
        check(deviceAdmission.validTicket(ticket)) {
            "Android device authentication expired"
        }
    }

    /** Called only after explicit creation consent; failed DB writes are preserved for recovery. */
    fun enroll(input: NativeCredentials.Enrollment, createdAt: String = Instant.now().toString()): String {
        ticket() // require explicit foreground device proof before any vault file is created
        val account = NativeCredentials.enroll(input, createdAt)
        val ownerId = UUID.randomUUID().toString()
        val issued = NativeCredentials.issueRecoveryCapability()
        vault.createFresh(legacyVaultPresent = false).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("INSERT INTO vault_account(" +
                    "id,email,first_name,last_name,password_salt,password_hash," +
                    "password_iterations,password_changed_at,q1_id,q1_hash,q2_id,q2_hash,created_at" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                        "native-account", account.email, account.firstName, account.lastName,
                        account.passwordSalt, account.passwordHash, account.passwordIterations,
                        account.passwordChangedAt, account.q1Id, account.q1Hash,
                        account.q2Id, account.q2Hash, account.createdAt))
                db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name," +
                    "relation,is_primary,created_at) VALUES (?,?,?,?,?,?)", arrayOf<Any>(
                        ownerId, "INDIVIDUAL", "${account.firstName} ${account.lastName}",
                        "Self", 1, createdAt))
                // Once a fresh SQLCipher vault exists, complete its first account
                // atomically even if Android backgrounds mid-creation. Aborting here
                // leaves an unowned encrypted file and wrapped Keystore key that may
                // not be silently deleted/recreated.
                writeSetting(db, RECOVERY_KEY, issued.verifier)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        session.lock()
        return issued.code
    }

    /** Never creates or resets a vault; wrong secrets grant no session. */
    fun login(email: String, password: String): Boolean =
        loginResult(email, password) == SignInResult.UNLOCKED

    /** Encrypted, transactionally persisted rate limit shared across login and recovery.
     * Clock rollback fails closed. Device time acceleration cannot be completely defeated offline.
     */
    @Synchronized fun loginResult(email: String, password: String): SignInResult {
        lock()
        return try {
            val admissionTicket = ticket()
            var account: NativeCredentials.Account? = null
            var valid = false
            vault.openExisting().use { db ->
                db.beginTransaction()
                try {
                    val now = wallClockMillis()
                    val prior = readThrottle(db)
                    val state = admitted(prior, now)
                    if (state != null) {
                        account = readOneAccount(db)
                        valid = account != null && NativeCredentials.verifyPassword(
                            account!!, email, password)
                        saveThrottle(db, if (valid) Throttle(0, now, 0)
                            else denied(state, now))
                    }
                    requireTicket(admissionTicket)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            requireTicket(admissionTicket)
            if (!valid || account == null) SignInResult.DENIED
            else if (NativeCredentials.passwordExpired(account!!, Instant.now()))
                SignInResult.ROTATION_REQUIRED
            else {
                session = NativeSession()
                if (session.login(account!!, email, password, SystemClock.elapsedRealtime()) &&
                    deviceAdmission.validTicket(admissionTicket)) {
                    sessionPasswordHash = account!!.passwordHash
                    SignInResult.UNLOCKED
                } else SignInResult.DENIED
            }
        } catch (_: Exception) {
            session.lock()
            SignInResult.DENIED
        }
    }

    /** An old-password proof is required even after 180-day expiry.
     * Explicit OS-device proof must be enforced by the calling activity.
     */
    @Synchronized fun rotatePassword(email: String, oldPassword: String, newPassword: String,
        changedAt: String = Instant.now().toString()) {
        val admissionTicket = ticket()
        lock()
        var success = false
        vault.openExisting().use { db ->
            db.beginTransaction()
            try {
                val now = wallClockMillis()
                val state = admitted(readThrottle(db), now)
                if (state != null) {
                    val record = readOneAccount(db)
                    if (record != null && NativeCredentials.verifyPassword(
                            record, email, oldPassword)) {
                        val changed = NativeCredentials.changePassword(
                            record, newPassword, changedAt, oldPassword)
                        updatePassword(db, record, changed)
                        saveThrottle(db, Throttle(0, now, 0))
                        success = true
                    } else saveThrottle(db, denied(state, now))
                }
                requireTicket(admissionTicket)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        require(success) { "Invalid account credentials or password" }
    }

    /** No reset using security questions alone. Absent provisioned code is a hard denial.
     * Verification and single-use verifier consumption are one encrypted SQL transaction.
     */
    @Synchronized fun recoverWithCode(email: String, code: String,
        q1Id: String, answer1: String, q2Id: String, answer2: String,
        newPassword: String, changedAt: String = Instant.now().toString()) {
        val admissionTicket = ticket()
        lock()
        var success = false
        vault.openExisting().use { db ->
            db.beginTransaction()
            try {
                val now = wallClockMillis()
                val state = admitted(readThrottle(db), now)
                if (state != null) {
                    val record = readOneAccount(db)
                    val verifier = readSetting(db, RECOVERY_KEY)
                    val changed = if (record == null || verifier == null) null
                        else runCatching {
                            NativeCredentials.recoverPassword(record, email, code, verifier,
                                q1Id, answer1, q2Id, answer2, newPassword, changedAt)
                        }.getOrNull()
                    if (changed != null && record != null) {
                        updatePassword(db, record, changed)
                        db.execSQL("DELETE FROM setting WHERE key=?", arrayOf<Any>(RECOVERY_KEY))
                        saveThrottle(db, Throttle(0, now, 0))
                        success = true
                    } else saveThrottle(db, denied(state, now))
                }
                requireTicket(admissionTicket)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        require(success) { "Recovery proof rejected" }
    }

    /** One-time replacement must start from an already password-verified session. */
    @Synchronized fun replaceRecoveryCode(email: String): String {
        val admissionTicket = ticket()
        val issued = NativeCredentials.issueRecoveryCapability()
        vault.openExisting().use { db ->
            db.beginTransaction()
            try {
                requireBoundCredential(db, email)
                writeSetting(db, RECOVERY_KEY, issued.verifier)
                requireTicket(admissionTicket)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        return issued.code
    }

    private fun updatePassword(db: SQLiteDatabase, old: NativeCredentials.Account,
        replacement: NativeCredentials.Account) {
        db.execSQL("UPDATE vault_account SET password_salt=?,password_hash=?," +
            "password_iterations=?,password_changed_at=? WHERE email=? AND password_hash=?",
            arrayOf<Any>(replacement.passwordSalt, replacement.passwordHash,
                replacement.passwordIterations, replacement.passwordChangedAt,
                old.email, old.passwordHash))
        db.rawQuery("SELECT changes()", null).use { c ->
            require(c.moveToFirst() && c.getLong(0) == 1L) {
                "Password update not applied"
            }
        }
    }

    /** A session only authenticates the exact credential hash it was issued for.
     * A password rotation/recovery by another instance immediately invalidates it.
     * Never rely solely on a mutable Compose or Activity unlocked flag.
     */
    private fun requireBoundCredential(db: SQLiteDatabase, email: String) {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val record = readOneAccount(db)
        val expected = sessionPasswordHash
        val valid = expected != null && record != null &&
            record.email == normalized &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8),
                record.passwordHash.toByteArray(Charsets.UTF_8)) &&
            !NativeCredentials.passwordExpired(record, Instant.now()) &&
            session.allowed(normalized, SystemClock.elapsedRealtime())
        if (!valid) {
            lock()
            throw IllegalStateException("Account session is locked or revoked")
        }
    }

    @Synchronized fun <T> withUnlockedVault(email: String, action: (SQLiteDatabase) -> T): T {
        val admissionTicket = ticket()
        return vault.openExisting().use { db ->
            db.beginTransaction()
            try {
                requireTicket(admissionTicket)
                requireBoundCredential(db, email)
                val result = action(db)
                requireTicket(admissionTicket)
                requireBoundCredential(db, email)
                db.setTransactionSuccessful()
                result
            } finally { db.endTransaction() }
        }
    }

    fun lock() {
        sessionPasswordHash = null
        session.lock()
    }
    fun onBackground() = lock()

    private fun readOneAccount(db: SQLiteDatabase): NativeCredentials.Account? {
        db.rawQuery("SELECT email,first_name,last_name,password_salt,password_hash," +
            "password_iterations,q1_id,q1_hash,q2_id,q2_hash,password_changed_at,created_at " +
            "FROM vault_account", null).use { cur ->
            if (!cur.moveToFirst()) return null
            val account = NativeCredentials.Account(
                cur.getString(0), cur.getString(1), cur.getString(2), cur.getString(3),
                cur.getString(4), cur.getInt(5), cur.getString(6), cur.getString(7),
                cur.getString(8), cur.getString(9), cur.getString(10), cur.getString(11))
            require(!cur.moveToNext()) { "Ambiguous native vault accounts" }
            return account
        }
    }
}