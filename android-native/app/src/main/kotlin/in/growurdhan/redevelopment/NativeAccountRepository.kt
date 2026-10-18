package `in`.growurdhan.redevelopment

import android.content.Context
import android.os.SystemClock
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeSession
import java.time.Instant
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Explicit enrollment/login boundary. UI remains locked until a separate M25 UI flow is approved. */
internal class NativeAccountRepository(context: Context) {
    private val vault = EncryptedVaultStore(context)
    private val session = NativeSession()

    /** Called only after explicit creation consent; failed DB writes are preserved for recovery. */
    fun enroll(input: NativeCredentials.Enrollment, createdAt: String = Instant.now().toString()) {
        val account = NativeCredentials.enroll(input, createdAt)
        val ownerId = UUID.randomUUID().toString()
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
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        session.lock()
    }

    /** Never creates or resets a vault; incorrect password grants no session. */
    fun login(email: String, password: String): Boolean {
        val record = try {
            vault.openExisting().use { db -> readOneAccount(db) }
        } catch (_: Exception) {
            session.lock()
            return false
        }
        if (record == null) { session.lock(); return false }
        return session.login(record, email, password, SystemClock.elapsedRealtime())
    }

    fun <T> withUnlockedVault(email: String, action: (SQLiteDatabase) -> T): T {
        check(session.allowed(email.trim().lowercase(java.util.Locale.ROOT),
            SystemClock.elapsedRealtime())) { "Account session is locked" }
        return vault.openExisting().use(action)
    }
    fun lock() = session.lock()
    fun onBackground() = session.onBackground()

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