package `in`.growurdhan.redevelopment

import android.app.KeyguardManager
import android.content.Context
import android.util.AtomicFile
import `in`.growurdhan.database.EncryptedRestorePublisher
import `in`.growurdhan.database.NativeV9Archive
import `in`.growurdhan.database.NativeV9RecoveryProof
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.database.RestoreOnceJournal
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Native-only new-device recovery path. Never merges/replaces an existing vault.
 * Separate from legacy KOSHA1/GUDVLT01 restore. No automatic UI or SAF dispatch.
 * Owner credential proof, OS device security, Terms, immutable review, durable
 * replay claim and independent wrapped key all precede no-overwrite publication.
 *
 * A crash after claim deliberately leaves artifacts for manual forensic recovery.
 */
internal class NativeV9NewDeviceRestore(
    private val context: Context,
    private val faultAt: ((Boundary) -> Unit)? = null
) {
    internal enum class Boundary {
        BEFORE_CLAIM, AFTER_CLAIM, AFTER_KEY_CREATION,
        AFTER_CANDIDATE_COMMIT, AFTER_CANDIDATE_VERIFIED,
        AFTER_KEY_PUBLICATION, AFTER_DATABASE_PUBLICATION
    }
    data class Review(val archiveSha256: String, val snapshotSha256: String,
        val rows: Int, val ownerApproved: Boolean)
    private val pending get() = File(context.noBackupFilesDir, "restore-pending.v1.kosha")
    private val magic = "GUDNAT01".toByteArray(Charsets.US_ASCII)
    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    private fun wipe(snapshot: NativeV9Archive.Snapshot) {
        snapshot.tables.forEach { t -> t.rows.forEach { row ->
            row.forEach { it.payload.fill(0) }
        } }
    }

    /** Only ciphertext is persisted. Staging is one-time, no replacement. */
    fun stageNativeOnce(archive: ByteArray): String =
        VaultLifecycleLock.exclusively(context) {
            require(archive.size in 56..NativeV9Archive.LIMIT &&
                archive.copyOfRange(0, 8).contentEquals(magic)) {
                "Native encrypted archive required"
            }
            UnusedVaultTargetGuard.requireUnused(context,
                allowAlreadyStagedArchive = false)
            val target = pending.toPath()
            check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(target.resolveSibling(pending.name + ".new"),
                    LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(target.resolveSibling(pending.name + ".bak"),
                    LinkOption.NOFOLLOW_LINKS)) {
                "Another restore or interrupted staging already exists"
            }
            val atomic = AtomicFile(pending)
            val stream = atomic.startWrite()
            try {
                stream.write(archive)
                atomic.finishWrite(stream)
            } catch (problem: Exception) {
                atomic.failWrite(stream)
                throw problem
            }
            check(StableStagedArchiveReader.read(target).contentEquals(archive)) {
                "Committed encrypted native archive differs from source"
            }
            digest(archive)
        }

    /** No mutation; authenticates BOTH backup and account recovery evidence. */
    fun preview(credentials: RestoreIdentityGate.Credentials,
        backupPassword: CharArray,
        consent: NativeRegistrationProof.Consent): Review {
        NativeRegistrationProof.requireValidConsent(consent)
        val archive = StableStagedArchiveReader.read(pending.toPath())
        require(archive.copyOfRange(0, 8).contentEquals(magic)) {
            "The staged archive is not the native format"
        }
        val source = try {
            NativeV9Archive.decrypt(archive, credentials.email, backupPassword)
        } catch (failure: Exception) {
            archive.fill(0)
            throw failure
        }
        try {
            val proof = NativeV9RecoveryProof.authenticate(source, credentials, consent)
            return Review(digest(archive), proof.snapshotSha256,
                proof.rowCount, ownerApproved = false)
        } finally { wipe(source); archive.fill(0) }
    }

    /**
     * No user interface calls this method yet: physical device proof and
     * owner-attested new-device UX remain separate release gates.
     */
    fun publishAfterOwnerReview(credentials: RestoreIdentityGate.Credentials,
        backupPassword: CharArray, consent: NativeRegistrationProof.Consent,
        reviewed: Review, explicitOwnerApproval: Boolean,
        deviceAdmissionStillValid: () -> Boolean): Int {
        require(explicitOwnerApproval && reviewed.ownerApproved) {
            "New device owner must explicitly approve a matching preview"
        }
        NativeRegistrationProof.requireValidConsent(consent)
        val device = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        check(device?.isDeviceSecure == true) {
            "Secure Android device lock is required for native recovery"
        }
        return VaultLifecycleLock.exclusively(context) {
            check(deviceAdmissionStillValid()) {
                "Fresh native recovery denied: Android OS admission is no longer valid"
            }
            UnusedVaultTargetGuard.requireUnused(context,
                allowAlreadyStagedArchive = true)
            val archive = StableStagedArchiveReader.read(pending.toPath())
            val source = try {
                require(archive.copyOfRange(0, 8).contentEquals(magic))
                NativeV9Archive.decrypt(archive, credentials.email, backupPassword)
            } catch (failure: Exception) {
                archive.fill(0)
                throw failure
            }
            try {
                val proof = NativeV9RecoveryProof.authenticate(source, credentials, consent)
                require(reviewed.copy(ownerApproved = false) ==
                    Review(digest(archive), proof.snapshotSha256, proof.rowCount, false)) {
                    "Authenticated archive or owner review changed"
                }
                check(deviceAdmissionStillValid()) {
                    "Fresh native recovery OS admission revoked during owner verification"
                }
                val dir = File(context.noBackupFilesDir, "restore-claims").toPath()
                if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS))
                    Files.createDirectory(dir)
                val journal = RestoreOnceJournal(dir)
                // Stable source fingerprint: re-encrypting the same snapshot with
                // fresh GCM randomness cannot evade the once-only replay gate.
                faultAt?.invoke(Boundary.BEFORE_CLAIM)
                journal.claimOnce(proof.snapshotSha256)
                // A new-device Terms event is independently durable without
                // mutating any original financial or historical source cell.
                NativeV9RestoreConsentJournal.recordOnce(
                    context.noBackupFilesDir.toPath(), proof.snapshotSha256, consent)
                faultAt?.invoke(Boundary.AFTER_CLAIM)

                val nonce = UUID.randomUUID().toString()
                val keyName = "vault-key.v1.restore-" + nonce
                val keyFile = File(context.noBackupFilesDir, keyName)
                val target = context.getDatabasePath("growurdhan-v9.sqlcipher")
                val candidate = File(target.parentFile,
                    "growurdhan-v9.sqlcipher.restore-" + nonce)
                check(!Files.exists(keyFile.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    !Files.exists(candidate.toPath(), LinkOption.NOFOLLOW_LINKS))
                val key = KeystoreWrappedVaultKey(context, keyName)
                    .getOrCreateForFreshInstall(legacyVaultPresent = false)
                val inserted: Int
                try {
                    faultAt?.invoke(Boundary.AFTER_KEY_CREATION)
                    inserted = EncryptedVaultStore(context)
                        .createNewRestoreCandidate(candidate, key).use { db ->
                            NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                                db, archive, credentials.email, backupPassword, true)
                        }
                    faultAt?.invoke(Boundary.AFTER_CANDIDATE_COMMIT)
                    val preserve = DatabaseErrorHandler { _, _ ->
                        throw IllegalStateException("Native candidate corruption; preserve")
                    }
                    System.loadLibrary("sqlcipher")
                    SQLiteDatabase.openDatabase(candidate.absolutePath, key, null,
                        SQLiteDatabase.OPEN_READONLY, preserve, null).use { db ->
                        val reopened = NativeV9SnapshotReader.capture(db)
                        try { require(reopened == source && inserted == proof.rowCount) {
                            "Closed native restore candidate mismatch"
                        } } finally { wipe(reopened) }
                    }
                    faultAt?.invoke(Boundary.AFTER_CANDIDATE_VERIFIED)
                } finally { key.fill(0) }
                check(!Files.exists(File(keyFile.path + ".bak").toPath(),
                    LinkOption.NOFOLLOW_LINKS) &&
                    !Files.exists(File(keyFile.path + ".new").toPath(),
                        LinkOption.NOFOLLOW_LINKS)) {
                    "Incomplete candidate key envelope; manual recovery required"
                }
                val liveKey = File(context.noBackupFilesDir, "vault-key.v1")
                NativeV9RestoreConsentJournal.requireExact(
                    context.noBackupFilesDir.toPath(), proof.snapshotSha256, consent)
                require(digest(StableStagedArchiveReader.read(pending.toPath())) ==
                    reviewed.archiveSha256) { "Staged ciphertext changed before publication" }
                // A revoked foreground ticket must not publish a key envelope.
                check(deviceAdmissionStillValid()) {
                    "Fresh native recovery OS admission revoked before key publication"
                }
                // Both names are exclusively reserved. No delete/retry on crash.
                EncryptedRestorePublisher.publishReservedAtomic(
                    keyFile.toPath(), liveKey.toPath())
                faultAt?.invoke(Boundary.AFTER_KEY_PUBLICATION)
                // If revoked between publication phases, preserve key, claim,
                // candidate and staging for explicit, reauthenticated manual resume.
                check(deviceAdmissionStillValid()) {
                    "Fresh native recovery OS admission revoked before database publication"
                }
                EncryptedRestorePublisher.publishReservedAtomic(
                    candidate.toPath(), target.toPath())
                faultAt?.invoke(Boundary.AFTER_DATABASE_PUBLICATION)
                check(digest(StableStagedArchiveReader.read(pending.toPath())) ==
                    reviewed.archiveSha256) { "Staged ciphertext changed at publication" }
                inserted
            } finally { wipe(source); archive.fill(0) }
        }
    }
}
