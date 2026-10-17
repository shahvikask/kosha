package `in`.growurdhan.redevelopment

import android.content.Context
import android.database.Cursor
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.database.RestoreV9Plan
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Owner-credential-gated FORENSIC INSPECTION only. Does not recover, delete,
 * unlink, rewrite, re-claim or publish any restore artifact or SQLite row.
 *
 * Every state, including VERIFIED_*, remains BLOCKED for normal restore.
 * Source authentication comes from the original staged GCM archive, not from
 * an untrusted filename, claim file, or decrypted candidate setting alone.
 * The caller must never use a VERIFIED_* result as permission to publish.
 */
internal class RestoreRecoveryInspector(private val context: Context) {
    enum class State { CLAIM_ONLY, INCOMPLETE, AMBIGUOUS, VERIFIED_CANDIDATE, VERIFIED_LIVE }
    data class Review(val state: State, val verifiedRows: Int = 0,
        val durableClaimPresent: Boolean = false, val liveReservationPresent: Boolean = false)

    private val fingerprintName = Regex("restore-([0-9a-f]{64})\\.claim")
    private val candidateDbName = Regex("growurdhan-v9\\.sqlcipher\\.restore-([0-9a-f-]{36})")
    private val candidateKeyName = Regex("vault-key\\.v1\\.restore-([0-9a-f-]{36})")
    private val tables = listOf("vault_account", "entity", "account", "instrument",
        "holding", "ledger_event", "goal", "setting")

    /** No mutable file operation is permitted, even for malformed evidence. */
    fun inspectForOwnerReview(
        credentials: RestoreIdentityGate.Credentials,
        explicitOwnerApproval: Boolean
    ): Review {
        check(explicitOwnerApproval) { "Explicit owner approval is required for forensic review" }
        return VaultLifecycleLock.exclusively(context) {
            val noBackup = context.noBackupFilesDir
            val target = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val parent = target.parentFile ?: error("Missing protected database directory")
            val pending = File(noBackup, "restore-pending.v1.kosha")
            check(regular(pending) && !exists(File(pending.path + ".bak"))) {
                "Missing or ambiguous staged archive; do not modify AtomicFile evidence"
            }
            // A clean baseFile is required because AtomicFile.openRead may
            // otherwise reconcile .bak and mutate forensic evidence.
            val beforePending = fingerprint(pending)
            val plan = LockedRestoreStaging(context).previewAuthenticatedV9(credentials)
            check(fingerprint(pending) == beforePending) {
                "Authenticated archive changed during forensic review"
            }
            val claimFiles = children(File(noBackup, "restore-claims"))
            val keys = children(noBackup).filter {
                it.name.startsWith("vault-key.v1.restore-")
            }
            val dbArtifacts = children(parent).filter {
                it.name.startsWith("growurdhan-v9.sqlcipher.restore-")
            }
            val liveKey = File(noBackup, "vault-key.v1")
            val liveDb = target
            val liveKeyPresent = exists(liveKey) || exists(File(liveKey.path + ".bak"))
            val liveDbPresent = exists(liveDb)
            val reservation = liveDbPresent && regular(liveDb) && liveDb.length() == 0L

            // Refuse unexpected claim formats or multiple fingerprint claims,
            // even if one of them matches the authenticated source.
            if (claimFiles.size != 1 ||
                claimFiles.any { !regular(it) || !fingerprintName.matches(it.name) }) {
                return@exclusively Review(State.AMBIGUOUS, durableClaimPresent = claimFiles.isNotEmpty())
            }
            val claim = claimFiles.single()
            val expectedClaim = "restore-" + plan.originalJsonSha256 + ".claim"
            if (claim.name != expectedClaim ||
                !Files.readAllBytes(claim.toPath()).contentEquals(
                    ("GUD_RESTORE_CLAIM_V1\n" + plan.originalJsonSha256 + "\n")
                        .toByteArray(Charsets.US_ASCII))) {
                return@exclusively Review(State.AMBIGUOUS, durableClaimPresent = true)
            }
            val claimBefore = fingerprint(claim)

            // Any SQLCipher WAL/SHM/journal, symlink or unknown candidate
            // suffix precludes read-only inspection and needs manual review.
            if (keys.any { !regular(it) || !candidateKeyName.matches(it.name) } ||
                dbArtifacts.any { !regular(it) || !candidateDbName.matches(it.name) } ||
                keys.size > 1 || dbArtifacts.size > 1 ||
                (liveDbPresent && !regular(liveDb)) ||
                (liveKeyPresent && (!regular(liveKey) ||
                    exists(File(liveKey.path + ".bak"))))) {
                return@exclusively Review(State.AMBIGUOUS, durableClaimPresent = true,
                    liveReservationPresent = reservation)
            }
            val keyCandidate = keys.singleOrNull()
            val dbCandidate = dbArtifacts.singleOrNull()
            val keyNonce = keyCandidate?.let { candidateKeyName.matchEntire(it.name)!!.groupValues[1] }
            val dbNonce = dbCandidate?.let { candidateDbName.matchEntire(it.name)!!.groupValues[1] }
            if (keyNonce != null && dbNonce != null && keyNonce != dbNonce)
                return@exclusively Review(State.AMBIGUOUS, durableClaimPresent = true,
                    liveReservationPresent = reservation)

            val selected: File
            val wrappedKeyName: String
            val state: State
            if (dbCandidate != null && !liveKeyPresent && keyCandidate != null &&
                !liveDbPresent) {
                selected = dbCandidate
                wrappedKeyName = keyCandidate.name
                state = State.VERIFIED_CANDIDATE
            } else if (dbCandidate != null && liveKeyPresent && keyCandidate == null &&
                (!liveDbPresent || reservation)) {
                // Crash between key publication and SQLCipher publication.
                selected = dbCandidate
                wrappedKeyName = "vault-key.v1"
                state = State.VERIFIED_CANDIDATE
            } else if (dbCandidate == null && keyCandidate == null &&
                liveKeyPresent && liveDbPresent && !reservation) {
                // Crash after both were published but before the receipt.
                selected = liveDb
                wrappedKeyName = "vault-key.v1"
                state = State.VERIFIED_LIVE
            } else {
                return@exclusively Review(if (!liveKeyPresent &&
                    keyCandidate == null && dbCandidate == null && !liveDbPresent)
                    State.CLAIM_ONLY else State.INCOMPLETE,
                    durableClaimPresent = true, liveReservationPresent = reservation)
            }
            check(regular(selected) && selected.length() > 0L) {
                "Encrypted forensic candidate is missing or empty"
            }
            val keyFile = File(noBackup, wrappedKeyName)
            check(regular(keyFile) && !exists(File(keyFile.path + ".bak"))) {
                "Wrapped candidate key missing or ambiguous"
            }
            for (file in listOf(selected, liveDb)) {
                for (suffix in listOf("-journal", "-wal", "-shm"))
                    check(!exists(File(file.path + suffix))) {
                        "Uncheckpointed encrypted restore sidecar; forensic review required"
                    }
            }
            val databaseBefore = fingerprint(selected)
            val keyBefore = fingerprint(keyFile)
            val key = KeystoreWrappedVaultKey(context, wrappedKeyName).unwrapExisting()
            try {
                System.loadLibrary("sqlcipher")
                val preserve = DatabaseErrorHandler { _, _ ->
                    throw IllegalStateException("Corrupt forensic candidate; never delete")
                }
                SQLiteDatabase.openDatabase(selected.absolutePath, key, null,
                    SQLiteDatabase.OPEN_READONLY, preserve, null).use { db ->
                    db.execSQL("PRAGMA query_only=ON")
                    verifyEncryptedRows(db, plan)
                }
            } finally {
                key.fill(0)
            }
            check(databaseBefore == fingerprint(selected) &&
                keyBefore == fingerprint(keyFile) &&
                claimBefore == fingerprint(claim) &&
                beforePending == fingerprint(pending)) {
                "Forensic source or candidate changed during read-only inspection"
            }
            // Refuse even an otherwise valid result if SQLite created a WAL
            // sidecar during the read. No cleanup is attempted here.
            for (file in listOf(selected, liveDb)) {
                for (suffix in listOf("-journal", "-wal", "-shm"))
                    check(!exists(File(file.path + suffix))) {
                        "Forensic inspection created an unexpected sidecar"
                    }
            }
            Review(state, plan.rows.size, true, reservation)
        }
    }

    private fun verifyEncryptedRows(db: SQLiteDatabase, plan: RestoreV9Plan.Plan) {
        fun scalar(query: String): String = db.rawQuery(query, null).use { cur ->
            require(cur.moveToFirst() && !cur.isNull(0))
            val value = cur.getString(0)
            require(!cur.moveToNext())
            value
        }
        require(scalar("PRAGMA cipher_version").isNotBlank()) { "Not SQLCipher" }
        require(scalar("PRAGMA quick_check") == "ok") { "SQLCipher SQLite integrity failed" }
        db.rawQuery("PRAGMA cipher_integrity_check", null).use {
            require(!it.moveToFirst()) { "SQLCipher HMAC verification failed" }
        }
        require(scalar("PRAGMA user_version") == "9") { "Invalid recovered schema version" }
        require(scalar("SELECT value FROM vault_meta WHERE key='schema_version'") == "9") {
            "Invalid recovered schema metadata"
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use {
            require(!it.moveToFirst()) { "Broken owner or account foreign key" }
        }
        for ((name, operation) in listOf(
            "tg_ledger_event__no_update" to "BEFORE UPDATE",
            "tg_ledger_event__no_delete" to "BEFORE DELETE")) {
            db.rawQuery("SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?",
                arrayOf(name)).use { cur ->
                require(cur.moveToFirst()) { "Missing immutable financial ledger trigger" }
                val ddl = cur.getString(0).uppercase()
                require(ddl.contains(operation) && ddl.contains("ON LEDGER_EVENT") &&
                    ddl.contains("RAISE(ABORT")) { "Unsafe ledger trigger" }
            }
        }

        // Compare EVERY projected typed value, not only table counts or the
        // original JSON SHA. This catches missing/misassociated custody and
        // financial metadata even if the candidate has the right row counts.
        require(plan.rows.all { it.table in tables })
        for (table in tables) {
            val expected = plan.rows.filter { it.table == table }
            val columns = expected.firstOrNull()?.columns
            if (columns == null) {
                require(scalar("SELECT COUNT(*) FROM $table") == "0") {
                    "Unexpected imported data in empty table"
                }
                continue
            }
            require(expected.all { it.columns == columns } &&
                columns.isNotEmpty() &&
                columns.all { it.matches(Regex("[a-z][a-z0-9_]*")) })
            val counts = expected.groupingBy { it.values }.eachCount().toMutableMap()
            val selected = columns.joinToString(",")
            db.rawQuery("SELECT $selected FROM $table", null).use { cur ->
                while (cur.moveToNext()) {
                    val actual = (0 until cur.columnCount).map { i ->
                        when (cur.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cur.getLong(i)
                            Cursor.FIELD_TYPE_STRING -> cur.getString(i)
                            else -> throw IllegalStateException("Unsafe restored row type")
                        }
                    }
                    val remaining = counts[actual] ?: 0
                    require(remaining > 0) { "Recovered row differs from authenticated snapshot" }
                    if (remaining == 1) counts.remove(actual)
                    else counts[actual] = remaining - 1
                }
            }
            require(counts.isEmpty()) { "Authenticated restored row missing" }
        }
    }

    private fun exists(file: File) = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun regular(file: File) = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(file.toPath())
    private fun children(dir: File): List<File> {
        if (!exists(dir)) return emptyList()
        require(Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(dir.toPath())) { "Unsafe forensic directory" }
        return dir.listFiles()?.toList() ?: error("Unreadable forensic directory")
    }
    private fun fingerprint(file: File): String {
        require(regular(file)) { "Invalid forensic artifact" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
