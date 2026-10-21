package `in`.growurdhan.redevelopment

import android.app.KeyguardManager
import android.content.Context
import `in`.growurdhan.database.EncryptedRestorePublisher
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Deliberate owner-attested continuation of ONE previously claimed and
 * completely verified native GUDNAT01 candidate. This is NOT a retry of the
 * original restore; claimOnce is NEVER called, claims/Terms are NEVER cleared.
 *
 * The forensic verifier, source review, and atomic publications run under the
 * SAME OS file lock. An ambiguous candidate, empty reservation, existing
 * database, unexpected artifact, or unreadable Keystore key fails closed.
 */
internal class NativeV9ManualResume(
    private val context: Context,
    private val atBoundary: ((Boundary) -> Unit)? = null
) {
    enum class Boundary { AFTER_KEY_PUBLICATION, AFTER_DATABASE_PUBLICATION }
    data class Offer(val archiveSha256: String, val snapshotSha256: String,
        val rows: Int, val state: NativeV9RecoveryInspector.State,
        val ownerApproved: Boolean = false)

    private val noFollow = LinkOption.NOFOLLOW_LINKS
    private fun present(f: File) = Files.exists(f.toPath(), noFollow)
    private fun matchSingle(folder: File, prefix: String, regex: Regex): File? {
        val files = folder.listFiles()?.filter { it.name.startsWith(prefix) }
            ?: if (present(folder)) error("Cannot read protected candidate folder") else emptyList()
        require(files.size <= 1 && files.all {
            regex.matches(it.name) &&
                Files.isRegularFile(it.toPath(), noFollow) &&
                !Files.isSymbolicLink(it.toPath())
        }) { "Ambiguous or unsafe native candidate" }
        return files.singleOrNull()
    }
    private val dbName = Regex("growurdhan-v9\\.sqlcipher\\.restore-[0-9a-f-]{36}")
    private val keyName = Regex("vault-key\\.v1\\.restore-[0-9a-f-]{36}")

    private fun reviewUnderLock(proof: RestoreIdentityGate.Credentials,
        password: CharArray, consent: NativeRegistrationProof.Consent): Offer {
        val inspection = NativeV9RecoveryInspector(context).inspectUnderLock(
            proof, password, consent, explicitOwnerApproval = true)
        require(inspection.state == NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE &&
            inspection.claimed && !inspection.liveReservation) {
            "Only an intact unpublished native candidate is eligible for manual resumption"
        }
        val source = NativeV9NewDeviceRestore(context).preview(proof, password, consent)
        require(source.rows == inspection.rows) {
            "Candidate row count changed since owner recovery proof"
        }
        return Offer(source.archiveSha256, source.snapshotSha256,
            source.rows, inspection.state)
    }

    /** No write, no cleanup; owner explicitly starts a NEW review session. */
    fun prepare(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent): Offer =
        VaultLifecycleLock.exclusively(context) {
            NativeRegistrationProof.requireValidConsent(consent)
            reviewUnderLock(proof, password, consent)
        }

    /**
     * Requires a fresh OS/device admission from MainActivity as well as
     * explicit owner approval. Never infer approval from VERIFIED_CANDIDATE.
     * If a publication fails, preserve the reservation and all candidate
     * evidence; a second call cannot silently delete or replace artifacts.
     */
    fun publish(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent, approved: Offer,
        deviceAdmissionStillValid: () -> Boolean): Int {
        require(approved.ownerApproved) { "Owner has not approved manual native resumption" }
        NativeRegistrationProof.requireValidConsent(consent)
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        check(keyguard?.isDeviceSecure == true) {
            "Manual native resumption requires a secure Android lock"
        }
        return VaultLifecycleLock.exclusively(context) {
            // Revalidate the Activity's exact generation-bound Keyguard admission
            // under the vault lock, not just before asynchronous IO dispatch.
            check(deviceAdmissionStillValid()) { "OS admission revoked before manual recovery" }
            val verified = reviewUnderLock(proof, password, consent)
            require(approved.copy(ownerApproved = false) == verified) {
                "Previously reviewed native candidate no longer matches"
            }
            check(deviceAdmissionStillValid()) { "OS admission revoked during candidate review" }
            val noBackup = context.noBackupFilesDir
            val database = context.getDatabasePath("growurdhan-v9.sqlcipher")
            val dbDirectory = database.parentFile ?: error("Missing native database folder")
            val db = matchSingle(dbDirectory,
                "growurdhan-v9.sqlcipher.restore-", dbName)
                ?: error("Verified candidate database no longer exists")
            val liveKey = File(noBackup, "vault-key.v1")
            val keyCandidate = matchSingle(noBackup, "vault-key.v1.restore-", keyName)
            check(!present(database)) { "Cannot overwrite an existing live or reserved vault" }
            check(deviceAdmissionStillValid()) { "OS admission revoked before key publication" }
            if (keyCandidate != null) {
                check(!present(liveKey)) { "Live key already occupied or linked" }
                val keyUuid = keyCandidate.name.removePrefix("vault-key.v1.restore-")
                val dbUuid = db.name.removePrefix("growurdhan-v9.sqlcipher.restore-")
                require(keyUuid == dbUuid) { "Candidate key/database identities differ" }
                EncryptedRestorePublisher.publishReservedAtomic(
                    keyCandidate.toPath(), liveKey.toPath())
            } else {
                // Crash strictly after key publication. Inspector already
                // decrypted and verified the candidate with that live key.
                check(Files.isRegularFile(liveKey.toPath(), noFollow) &&
                    !Files.isSymbolicLink(liveKey.toPath())) {
                    "Previously published wrapped SQLCipher key missing"
                }
            }
            atBoundary?.invoke(Boundary.AFTER_KEY_PUBLICATION)
            check(deviceAdmissionStillValid()) {
                "OS admission revoked between key and encrypted database publication"
            }
            // This never replaces an existing non-empty DB or tombstone.
            EncryptedRestorePublisher.publishReservedAtomic(
                db.toPath(), database.toPath())
            atBoundary?.invoke(Boundary.AFTER_DATABASE_PUBLICATION)
            val finalCheck = NativeV9RecoveryInspector(context).inspectUnderLock(
                proof, password, consent, explicitOwnerApproval = true)
            check(finalCheck.state == NativeV9RecoveryInspector.State.VERIFIED_LIVE &&
                finalCheck.rows == verified.rows) {
                "Published encrypted vault failed independent forensic verification"
            }
            verified.rows
        }
    }
}
