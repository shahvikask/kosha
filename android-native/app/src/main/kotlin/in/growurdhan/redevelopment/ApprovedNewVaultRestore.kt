package `in`.growurdhan.redevelopment

import android.content.Context
import `in`.growurdhan.database.EncryptedRestorePublisher
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.database.RestoreOnceJournal
import `in`.growurdhan.database.RestoreV9InsertTransaction
import `in`.growurdhan.database.RestoreV9Plan
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Owner-approved NEW-DEVICE encrypted restore, never a replacement/merge.
 *
 * No UI wires this automatically: explicit owner confirmation, non-masked PAN
 * evidence and the device/security acceptance policy remain upstream gates.
 * This method refuses incomplete legacy payloads rather than fabricating
 * prices, lots, opening balances or ledger events.
 *
 * Crash policy: durable journal claims NEVER roll back. An interrupted import
 * leaves a preserved encrypted candidate or a partial publication and needs
 * explicit forensic recovery. A retry cannot silently reset or replace a vault.
 */
internal class ApprovedNewVaultRestore(
    private val context: Context,
    // Tests may throw at an irreversible boundary. Production uses null.
    // This callback cannot authenticate, bypass, publish, or reset a claim.
    private val faultAt: ((Boundary) -> Unit)? = null
) {
    internal enum class Boundary {
        BEFORE_CLAIM, AFTER_CLAIM, AFTER_KEY_CREATION,
        AFTER_CANDIDATE_VERIFIED, BEFORE_KEY_PUBLICATION,
        AFTER_KEY_PUBLICATION, BEFORE_DATABASE_PUBLICATION,
        AFTER_DATABASE_PUBLICATION
    }
    private fun at(boundary: Boundary) { faultAt?.invoke(boundary) }
    data class Receipt(val authenticatedPlaintextSha256: String, val restoredRows: Int)

    @Synchronized
    fun restoreFromStagedNewVault(
        credentials: RestoreIdentityGate.Credentials,
        explicitOwnerApproval: Boolean
    ): Receipt {
        check(explicitOwnerApproval) { "Restore requires explicit owner confirmation" }
        return VaultLifecycleLock.exclusively(context) {
            val target = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val liveKey = File(context.noBackupFilesDir, "vault-key.v1")
            val liveKeyBak = File(liveKey.path + ".bak")
            val pending = File(context.noBackupFilesDir, "restore-pending.v1.kosha")
            check(!File(pending.path + ".new").exists()) {
                "Interrupted encrypted staging write; forensic inspection required"
            }
            check(pending.isFile || File(pending.path + ".bak").isFile) {
                "No staged encrypted archive to restore"
            }
            check(!target.exists() && !File(target.path + "-wal").exists() &&
                !File(target.path + "-shm").exists() &&
                !File(target.path + "-journal").exists() &&
                !liveKey.exists() && !liveKeyBak.exists() &&
                !File(context.filesDir, "vault.enc").exists() &&
                !File(context.noBackupFilesDir, "vault.enc").exists()) {
                "Existing or ambiguous vault state; never overwrite or recreate"
            }

            // A previous different archive may have claimed restore and left
            // an orphaned candidate/key. Never begin a second recovery over it.
            RestoreArtifactGuard.requireNoAbandonedRestore(context)

            // All GCM, password, PAN/DOB, two recovery answers and strict
            // original owner/ledger proofs precede a durable replay claim.
            val plan = LockedRestoreStaging(context)
                .previewAuthenticatedV9(credentials)
            val sourceFingerprint = plan.originalJsonSha256
            require(plan.rows.isNotEmpty()) { "Missing authenticated restore rows" }

            val journalDir = File(context.noBackupFilesDir, "restore-claims").toPath()
            if (!Files.exists(journalDir, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(journalDir)
            val journal = RestoreOnceJournal(journalDir)
            // Claim BEFORE any candidate generation. A failed attempt is
            // intentionally permanent until an owner-reviewed recovery.
            at(Boundary.BEFORE_CLAIM)
            journal.claimOnce(sourceFingerprint)
            at(Boundary.AFTER_CLAIM)

            val nonce = UUID.randomUUID().toString()
            val keyName = "vault-key.v1.restore-$nonce"
            val candidateKeyFile = File(context.noBackupFilesDir, keyName)
            val candidate = File(target.parentFile,
                "growurdhan-v9.sqlcipher.restore-$nonce")
            check(!candidate.exists() && !candidateKeyFile.exists())
            val keyManager = KeystoreWrappedVaultKey(context, keyName)
            val newKey = keyManager.getOrCreateForFreshInstall(legacyVaultPresent = false)
            try {
                at(Boundary.AFTER_KEY_CREATION)
                // One canonical frozen-v9 initialization transaction, then one
                // INSERT-only transaction. The live database is NEVER opened.
                EncryptedVaultStore(context).createNewRestoreCandidate(candidate, newKey).use {
                    RestoreV9InsertTransaction.apply(plan, AndroidRestoreV9SqlTarget(it))
                }
                // Re-open the CLOSED candidate using its independent wrapped
                // key, and verify committed ciphertext and exact original JSON
                // before making any irreversible live-name publication.
                verifyClosedCandidate(candidate, newKey, plan)
                at(Boundary.AFTER_CANDIDATE_VERIFIED)
            } finally {
                newKey.fill(0)
            }

            check(!File(candidateKeyFile.path + ".bak").exists()) {
                "Incomplete wrapped candidate key; manual recovery required"
            }
            // The envelope is linked first. A crash in between leaves an
            // orphan live envelope, which blocks blank vault recreation.
            // The cipher is atomically published second via an exclusive
            // zero-byte reservation, never over an existing vault.\n            // Any failure leaves
            // the journal, pending archive and artifacts intact for review.
            at(Boundary.BEFORE_KEY_PUBLICATION)
            EncryptedRestorePublisher.publishReservedAtomic(
                candidateKeyFile.toPath(), liveKey.toPath())
            at(Boundary.AFTER_KEY_PUBLICATION)
            at(Boundary.BEFORE_DATABASE_PUBLICATION)
            EncryptedRestorePublisher.publishReservedAtomic(
                candidate.toPath(), target.toPath())
            at(Boundary.AFTER_DATABASE_PUBLICATION)
            Receipt(sourceFingerprint, plan.rows.size)
        }
    }

    private fun verifyClosedCandidate(file: File, key: ByteArray, plan: RestoreV9Plan.Plan) {
        val preserve = DatabaseErrorHandler { _, _ ->
            throw IllegalStateException("Corrupt restore candidate; preserve ciphertext")
        }
        SQLiteDatabase.openDatabase(file.absolutePath, key, null,
            SQLiteDatabase.OPEN_READWRITE, preserve, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            fun onlyString(sql: String, args: Array<String>? = null): String =
                db.rawQuery(sql, args).use { cursor ->
                    require(cursor.moveToFirst()) { "Missing restore verification row" }
                    val answer = cursor.getString(0)
                    require(!cursor.moveToNext()) { "Unexpected restore verification rows" }
                    answer
                }
            require(onlyString("PRAGMA cipher_version").isNotBlank()) {
                "Restore candidate not encrypted"
            }
            require(onlyString("PRAGMA quick_check") == "ok") {
                "Restore candidate SQLite integrity failed"
            }
            // SQLCipher 4.17 cipher_integrity_check returns ZERO rows when
            // the page-HMAC envelope is valid, and one row for EACH error.
            // Unlike SQLite quick_check, it does not return the text "ok".
            db.rawQuery("PRAGMA cipher_integrity_check", null).use { cur ->
                require(!cur.moveToFirst()) {
                    "Restore candidate SQLCipher page HMAC integrity failed"
                }
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use {
                require(!it.moveToFirst()) { "Restore candidate has invalid owner foreign keys" }
            }
            for (name in listOf("tg_ledger_event__no_update",
                    "tg_ledger_event__no_delete")) {
                require(onlyString("SELECT name FROM sqlite_master " +
                    "WHERE type='trigger' AND name=?", arrayOf(name)) == name) {
                    "Immutable ledger trigger missing from restored database"
                }
            }
            val original = onlyString(
                "SELECT value FROM setting WHERE key=?",
                arrayOf("m24.authenticated_frozen_original_json"))
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(original.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            require(digest == plan.originalJsonSha256) {
                "Restored original JSON fingerprint changed"
            }
            val requiredRows = plan.rows.groupingBy { it.table }.eachCount()
            for ((table, count) in requiredRows) {
                require(table.matches(Regex("[a-z][a-z0-9_]*")))
                val actual = db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                    check(it.moveToFirst())
                    it.getLong(0)
                }
                require(actual == count.toLong()) {
                    "Restored encrypted table count mismatch"
                }
            }
        }
    }
}
