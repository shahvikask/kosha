package `in`.growurdhan.redevelopment

import android.content.Context
import `in`.growurdhan.database.NativeV9Archive
import `in`.growurdhan.database.NativeV9RecoveryProof
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * GUDNAT01 crash forensic inspection: never delete, repair, retry, claim, or
 * publish an encrypted key, SQLCipher database, or original staged archive.
 *
 * VERIFIED_* means "owner-reviewable evidence" only, never authorization to
 * resume publication. Caller still needs new security/device acceptance.
 */
internal class NativeV9RecoveryInspector(private val context: Context) {
    enum class State { CLAIM_ONLY, INCOMPLETE, AMBIGUOUS, VERIFIED_CANDIDATE, VERIFIED_LIVE }
    data class Inspection(val state: State, val rows: Int = 0,
        val claimed: Boolean = false, val liveReservation: Boolean = false)
    private val noFollow = LinkOption.NOFOLLOW_LINKS
    private val candidateDb = Regex("growurdhan-v9\\.sqlcipher\\.restore-([0-9a-f-]{36})")
    private val candidateKey = Regex("vault-key\\.v1\\.restore-([0-9a-f-]{36})")
    private fun exists(file: File): Boolean = Files.exists(file.toPath(), noFollow)
    private fun regular(file: File): Boolean =
        Files.isRegularFile(file.toPath(), noFollow) && !Files.isSymbolicLink(file.toPath())
    private fun children(dir: File): List<File> {
        if (!exists(dir)) return emptyList()
        require(Files.isDirectory(dir.toPath(), noFollow) &&
            !Files.isSymbolicLink(dir.toPath())) { "Unsafe forensic recovery folder" }
        return dir.listFiles()?.toList() ?: error("Cannot enumerate recovery evidence")
    }
    private fun digest(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
    private fun digest(file: File): String {
        require(regular(file)) { "Missing encrypted recovery evidence" }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun wipe(s: NativeV9Archive.Snapshot) {
        s.tables.forEach { t -> t.rows.forEach { row ->
            row.forEach { it.payload.fill(0) }
        } }
    }
    fun inspect(credentials: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent,
        explicitOwnerApproval: Boolean): Inspection {
        require(explicitOwnerApproval) { "Owner must approve forensic review" }
        NativeRegistrationProof.requireValidConsent(consent)
        return VaultLifecycleLock.exclusively(context) {
            val noBackup = context.noBackupFilesDir
            val liveDb = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val dbDir = liveDb.parentFile ?: error("Missing encrypted database folder")
            val pending = File(noBackup, "restore-pending.v1.kosha")
            val before = digest(pending)
            val encrypted = StableStagedArchiveReader.read(pending.toPath())
            val source = try {
                require(encrypted.copyOfRange(0, 8).contentEquals(
                    "GUDNAT01".toByteArray(Charsets.US_ASCII)))
                NativeV9Archive.decrypt(encrypted, credentials.email, password)
            } catch (ex: Exception) {
                encrypted.fill(0)
                throw ex
            }
            try {
                val proof = NativeV9RecoveryProof.authenticate(source, credentials, consent)
                val fingerprint = proof.snapshotSha256
                val claimFiles = children(File(noBackup, "restore-claims"))
                val expected = File(noBackup, "restore-claims/restore-" +
                    fingerprint + ".claim")
                val claimBytes = ("GUD_RESTORE_CLAIM_V1\n" + fingerprint + "\n")
                    .toByteArray(Charsets.US_ASCII)
                require(claimFiles.size == 1 && claimFiles.single().name == expected.name &&
                    regular(expected) && Files.size(expected.toPath()) ==
                    claimBytes.size.toLong() &&
                    Files.readAllBytes(expected.toPath()).contentEquals(claimBytes)) {
                    "Missing or ambiguous permanent native restore claim"
                }
                NativeV9RestoreConsentJournal.requireExact(
                    noBackup.toPath(), fingerprint, consent)
                // Unknown or duplicate names, foreign candidates, or sidecar
                // ciphertext mean manual recovery: never choose by mtime.
                val keys = children(noBackup).filter {
                    it.name.startsWith("vault-key.v1.restore-")
                }
                val dbs = children(dbDir).filter {
                    it.name.startsWith("growurdhan-v9.sqlcipher.restore-")
                }
                val liveKey = File(noBackup, "vault-key.v1")
                val keyPresent = exists(liveKey)
                val dbPresent = exists(liveDb)
                val reservation = dbPresent && regular(liveDb) && liveDb.length() == 0L
                val ambiguous = keys.size > 1 || dbs.size > 1 ||
                    keys.any { !regular(it) || !candidateKey.matches(it.name) } ||
                    dbs.any { !regular(it) || !candidateDb.matches(it.name) } ||
                    (keyPresent && !regular(liveKey)) ||
                    (dbPresent && !regular(liveDb)) ||
                    listOf("vault-key.v1.new","vault-key.v1.bak").any {
                        exists(File(noBackup,it))
                    } || listOf(".new",".bak").any { suffix ->
                        exists(File(pending.path + suffix))
                    }
                if (ambiguous) return@exclusively Inspection(State.AMBIGUOUS,
                    claimed = true, liveReservation = reservation)
                val keyCandidate = keys.singleOrNull()
                val dbCandidate = dbs.singleOrNull()
                val nonceKey = keyCandidate?.let {
                    candidateKey.matchEntire(it.name)!!.groupValues[1]
                }
                val nonceDb = dbCandidate?.let {
                    candidateDb.matchEntire(it.name)!!.groupValues[1]
                }
                if (nonceKey != null && nonceDb != null && nonceKey != nonceDb)
                    return@exclusively Inspection(State.AMBIGUOUS, claimed = true,
                        liveReservation = reservation)
                val selected: File
                val selectedKey: File
                val state: State
                if (dbCandidate != null && !keyPresent && keyCandidate != null &&
                    !dbPresent) {
                    selected = dbCandidate; selectedKey = keyCandidate
                    state = State.VERIFIED_CANDIDATE
                } else if (dbCandidate != null && keyPresent &&
                    keyCandidate == null && (!dbPresent || reservation)) {
                    selected = dbCandidate; selectedKey = liveKey
                    state = State.VERIFIED_CANDIDATE
                } else if (dbCandidate == null && keyCandidate == null &&
                    keyPresent && dbPresent && !reservation) {
                    selected = liveDb; selectedKey = liveKey
                    state = State.VERIFIED_LIVE
                } else return@exclusively Inspection(
                    if (!keyPresent && keyCandidate == null && dbCandidate == null &&
                        !dbPresent) State.CLAIM_ONLY else State.INCOMPLETE,
                    claimed = true, liveReservation = reservation)
                if (!regular(selected) || selected.length() == 0L ||
                    !regular(selectedKey)) return@exclusively Inspection(
                    State.INCOMPLETE, claimed = true, liveReservation = reservation)
                for (file in listOf(selected,liveDb)) for (s in listOf("-wal","-shm","-journal"))
                    require(!exists(File(file.path + s))) {
                        "Encrypted candidate has pending SQLite journal"
                    }
                val beforeDb = digest(selected)
                val beforeKey = digest(selectedKey)
                val beforeClaim = digest(expected)
                val key = KeystoreWrappedVaultKey(context,selectedKey.name).unwrapExisting()
                try {
                    System.loadLibrary("sqlcipher")
                    val preserve = DatabaseErrorHandler { _, _ ->
                        throw IllegalStateException("Corrupt recovery candidate preserved")
                    }
                    SQLiteDatabase.openDatabase(selected.absolutePath, key, null,
                        SQLiteDatabase.OPEN_READONLY, preserve, null).use { db ->
                        val recovered = NativeV9SnapshotReader.capture(db)
                        try { require(recovered == source) {
                            "Candidate native data differs from authenticated archive"
                        } } finally { wipe(recovered) }
                    }
                } finally { key.fill(0) }
                require(digest(pending) == before && digest(selected) == beforeDb &&
                    digest(selectedKey) == beforeKey && digest(expected) == beforeClaim &&
                    digest(encrypted) == before) {
                    "Forensic source/candidate mutated during read-only review"
                }
                for (file in listOf(selected,liveDb)) for (s in listOf("-wal","-shm","-journal"))
                    require(!exists(File(file.path + s))) {
                        "Forensic read created unfinished SQLite sidecar"
                    }
                Inspection(state, proof.rowCount, claimed = true,
                    liveReservation = reservation)
            } finally { wipe(source); encrypted.fill(0) }
        }
    }
}
