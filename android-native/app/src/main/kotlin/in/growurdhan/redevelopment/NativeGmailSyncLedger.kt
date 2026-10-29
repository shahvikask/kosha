package `in`.growurdhan.redevelopment

import java.security.MessageDigest
import java.util.Locale
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Local SQLCipher per-Gmail-account import journal; NOT an OAuth client.
 * Google credentials/scopes and Gmail API access remain owner-blocked.
 * No attachment is marked seen without a reconciled SHA from the SAME owner.
 */
internal class NativeGmailSyncLedger(private val accounts: NativeAccountRepository) {
    data class MessageKey(val gmailAddress: String, val messageId: String,
        val attachmentId: String, val sourceSha256: String)
    data class Receipt(val added: Boolean, val accountFingerprint: String)
    private val id = Regex("[A-Za-z0-9_-]{4,200}")
    private val digest = Regex("[0-9a-f]{64}")
    private val emailRegex = Regex("[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}")

    private fun normalized(email: String): String {
        val result = email.trim().lowercase(Locale.ROOT)
        require(result.length <= 320 && emailRegex.matches(result))
        return result
    }
    private fun accountId(email: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalized(email).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun requireOptIn(db: SQLiteDatabase, owner: String) {
        db.rawQuery("SELECT gmail_sync FROM entity WHERE entity_id=? " +
            "AND is_active=1", arrayOf(owner)).use {
            require(it.moveToFirst() && it.getInt(0) == 1) {
                "Owner has not opted into Gmail synchronization"
            }
        }
    }
    private fun ownerKey(account: String, owner: String): String {
        require(owner.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        return account + "." + owner
    }

    private fun key(account: String, owner: String, message: MessageKey): String {
        require(id.matches(message.messageId) &&
            id.matches(message.attachmentId) && digest.matches(message.sourceSha256))
        return "native.gmail.seen." + ownerKey(account, owner) + "." +
            message.messageId + "." + message.attachmentId
    }

    /** Local owner consent does not grant or imply Google OAuth access. */
    fun setMemberOptIn(email: String, owner: String, approved: Boolean) {
        accounts.withUnlockedVault(email) { db ->
            db.rawQuery("SELECT 1 FROM entity WHERE entity_id=? AND is_active=1",
                arrayOf(owner)).use { require(it.moveToFirst()) }
            db.execSQL("UPDATE entity SET gmail_sync=? WHERE entity_id=?",
                arrayOf<Any>(if (approved) 1 else 0, owner))
        }
    }

    /** Explicit owner-approved mapping, kept inside the existing encrypted vault. */
    fun bindGmailAccount(email: String, owner: String, gmailAddress: String) {
        val normalizedAddress = normalized(gmailAddress)
        val hashed = accountId(normalizedAddress)
        accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            val mappingKey = "native.gmail.mapping." + ownerKey(hashed, owner)
            // A shared household inbox may be linked to multiple owners, but
            // each explicit link must be independently approved and encrypted.
            db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(mappingKey, owner))
        }
    }

    fun requireGmailAccountOwner(email: String, owner: String, gmailAddress: String) {
        val hashed = accountId(gmailAddress)
        accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf("native.gmail.mapping." + ownerKey(hashed, owner))).use {
                require(it.moveToFirst() && it.getString(0) == owner) {
                    "Gmail identity has no approved owner mapping"
                }
            }
        }
    }

    fun unlinkGmailAccount(email: String, owner: String, gmailAddress: String) {
        val hashed = accountId(gmailAddress)
        accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            val mappingKey = "native.gmail.mapping." + ownerKey(hashed, owner)
            db.execSQL("DELETE FROM setting WHERE key=? AND value=?",
                arrayOf<Any>(mappingKey, owner))
            db.execSQL("DELETE FROM setting WHERE key=?",
                arrayOf<Any>("native.gmail.cursor." + ownerKey(hashed, owner)))
        }
    }

    fun alreadySeen(email: String, owner: String, message: MessageKey): Boolean {
        requireGmailAccountOwner(email, owner, message.gmailAddress)
        val account = accountId(message.gmailAddress)
        return accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf(key(account, owner, message))).use { cur ->
                if (!cur.moveToFirst()) false
                else {
                    require(cur.getString(0) == message.sourceSha256) {
                        "Gmail message identity has conflicting source bytes"
                    }
                    true
                }
            }
        }
    }

    /** Mark only AFTER verified PDF import tied to the same family entity. */
    fun markReconciled(email: String, owner: String,
        message: MessageKey): Receipt {
        requireGmailAccountOwner(email, owner, message.gmailAddress)
        val account = accountId(message.gmailAddress)
        val index = key(account, owner, message)
        return accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            val imported = db.rawQuery(
                "SELECT 1 FROM source_document sd JOIN statement_position sp " +
                "ON sp.import_batch_id=sd.import_batch_id JOIN holding h " +
                "ON h.holding_id=sp.holding_id WHERE sd.sha256=? " +
                "AND h.entity_id=? LIMIT 1",
                arrayOf(message.sourceSha256, owner)).use { it.moveToFirst() }
            require(imported) {
                "Cannot mark an unimported/foreign-owner Gmail attachment seen"
            }
            val prior = db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf(index)).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            if (prior != null) {
                require(prior == message.sourceSha256) {
                    "Gmail attachment identity changed source bytes"
                }
                Receipt(false, account)
            } else {
                db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                    arrayOf<Any>(index, message.sourceSha256))
                Receipt(true, account)
            }
        }
    }

    /**
     * Caller must provide OAuth-verified provider's complete page report.
     * All message attachments must have a reconciled source fingerprint before
     * the cursor is committed. Cursor cannot move past missing pages.
     */
    fun advanceCompleteCursor(email: String, owner: String,
        gmailAddress: String, historyId: String,
        complete: Boolean, messages: List<MessageKey>) {
        require(complete && messages.isNotEmpty() &&
            historyId.matches(Regex("[0-9]{1,30}")))
        requireGmailAccountOwner(email, owner, gmailAddress)
        val account = accountId(gmailAddress)
        require(messages.all { accountId(it.gmailAddress) == account })
        accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            for (message in messages) {
                val proof = db.rawQuery("SELECT 1 FROM setting " +
                    "WHERE key=? AND value=?",
                    arrayOf(key(account, owner, message), message.sourceSha256))
                    .use { it.moveToFirst() }
                require(proof) { "Unreconciled attachment blocks Gmail history cursor" }
            }
            val cursorKey = "native.gmail.cursor." + ownerKey(account, owner)
            val previous = db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf(cursorKey)).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (previous != null) require(
                java.math.BigInteger(historyId) >= java.math.BigInteger(previous)) {
                "Gmail history cursor cannot move backwards"
            }
            db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(cursorKey, historyId))
        }
    }

    fun currentCursor(email: String, owner: String, gmailAddress: String): String? {
        requireGmailAccountOwner(email, owner, gmailAddress)
        val account = accountId(gmailAddress)
        return accounts.withUnlockedVault(email) { db ->
            requireOptIn(db, owner)
            db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf("native.gmail.cursor." + ownerKey(account, owner))).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
    }
}
