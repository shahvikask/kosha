package `in`.growurdhan.redevelopment

import android.content.Context
import android.util.AtomicFile
import `in`.growurdhan.database.LegacyBackupEnvelopeReader
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.database.RestoreV9Plan
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Fail-closed first phase of legacy restore. This class ONLY quarantines the
 * original encrypted archive and verifies its decryptable payload in memory.
 * It never replaces, clears or appends to the live SQLCipher ledger.
 *
 * Final identity (PAN/DOB/answers), replay policy and verified atomic ledger
 * application are intentionally NOT exposed here; M24/M25 gates remain OPEN.
 */
internal class LockedRestoreStaging(private val context: Context) {
    private val staged = AtomicFile(File(context.noBackupFilesDir, "restore-pending.v1.kosha"))
    private val magic = "GUDVLT01".toByteArray(Charsets.US_ASCII)
    private val maxBytes = 64 * 1024 * 1024

    /**
     * Legacy KOSHA1's outer JSON is not encrypted, but its cipherB64 still
     * authenticates through GCM. Reject malformed UTF-8 BEFORE JSON parsing:
     * a replacement character could alter envelope metadata or identifiers.
     */
    private fun legacyOuter(bytes: ByteArray): JSONObject {
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        return JSONObject(text)
    }

    private fun requireNoUnfinishedAtomicWrite() {
        // Android AtomicFile.openRead can delete a dangling .new file.
        // Never mutate or consume a partially written encrypted archive.
        check(!File(staged.baseFile.path + ".new").exists()) {
            "Interrupted encrypted restore staging; preserve .new for owner review"
        }
    }

    data class QuarantineReceipt(val sha256: String, val byteCount: Int)
    data class Inspection(
        val sha256: String,
        val accountEmail: String,
        val familyCount: Int,
        val holdingCount: Int,
        val ledgerCount: Int,
        val ownersConsistent: Boolean
    )

    @Synchronized
    fun stageEncryptedArchiveOnce(envelope: ByteArray): QuarantineReceipt =
        VaultLifecycleLock.exclusively(context) {
        require(envelope.size in 56..maxBytes) { "Unsupported or oversized encrypted archive" }
        val binary = envelope.copyOfRange(0, 8).contentEquals(magic)
        if (!binary) {
            // Unlocked JSON must never enter the restore quarantine.
            val outer = try { legacyOuter(envelope) }
                catch (_: Exception) { throw IllegalArgumentException("Unsupported backup envelope") }
            require(outer.optString("magic") == "KOSHA1" &&
                outer.has("cipherB64")) { "Unencrypted or unknown backup refused" }
        }
        // The file picker can outlive the preflight: a new native vault may
        // have appeared while SAF was open. Refuse to stage any archive after
        // such a race, UNDER the same cross-process vault lifecycle lock.
        UnusedVaultTargetGuard.requireUnused(
            context, allowAlreadyStagedArchive = false)
        val base = staged.baseFile.toPath()
        val dir = base.parent ?: error("Missing private restore directory")
        check(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(dir)) { "Unsafe restore staging directory" }
        check(!Files.exists(base, LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(dir.resolve(staged.baseFile.name + ".bak"),
                LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(dir.resolve(staged.baseFile.name + ".new"),
                LinkOption.NOFOLLOW_LINKS)) {
            "An encrypted restore or unfinished AtomicFile is already staged"
        }
        val out = staged.startWrite()
        try {
            out.write(envelope)
            staged.finishWrite(out)
        } catch (failure: Exception) {
            staged.failWrite(out)
            throw failure
        }
        // Verify the committed AtomicFile matches the exact bytes supplied,
        // without invoking openRead()/implicit .bak repair. A mismatch leaves
        // the protected artifact for explicit owner forensic review.
        check(StableStagedArchiveReader.read(base).contentEquals(envelope)) {
            "Staged encrypted archive differs from the approved input"
        }
        QuarantineReceipt(sha256(envelope), envelope.size)
    }

    /**
     * No state mutation. Consumer MUST implement complete account recovery,
     * ownership review, replay policy and transactional SQLCipher import before
     * using this data. Raw unknown JSON fields are never normalized or dropped.
     */
    @Synchronized
    fun inspect(email: String, password: String): Inspection {
        requireNoUnfinishedAtomicWrite()
        check(staged.baseFile.isFile) { "No canonical staged encrypted restore" }
        // Read the original archive without AtomicFile.openRead(): that API
        // can reconcile a .bak and change forensic evidence during inspection.
        // A partial .new/.bak is never silently repaired or consumed.
        val bytes = StableStagedArchiveReader.read(staged.baseFile.toPath())
        val plain = if (bytes.copyOfRange(0, 8).contentEquals(magic)) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(bytes, email, password)
        } else {
            val outer = legacyOuter(bytes)
            val blob = LegacyBackupEnvelopeReader.Kosha1(
                outer.getString("magic"),
                legacyKdf(outer), legacyRounds(outer), outer.getString("saltB64"),
                outer.getString("ivB64"), outer.getString("cipherB64")
            )
            LegacyBackupEnvelopeReader.decodeKOSHA1(blob, password)
        }
        val body = JSONObject(plain)
        // The frozen v2 exporter writes kind=GUDVAULT. The older KOSHA1
        // decryptor accepts an account without a kind field and supplies kind
        // itself during unpackLegacyJson; rejecting all kind-less blobs here
        // would make genuine older encrypted backups permanently unrestorable.
        val modern = bytes.copyOfRange(0, 8).contentEquals(magic)
        require(body.has("account") &&
            (if (modern) body.getString("kind") == "GUDVAULT"
             else !body.has("kind") || body.getString("kind") == "GUDVAULT")) {
            "Decrypted archive has incompatible payload type"
        }
        val account = body.getJSONObject("account")
        val storedEmail = account.getString("email")
        require(storedEmail.trim().equals(email.trim(), ignoreCase = true)) {
            "Backup account identity mismatch"
        }
        // The frozen KOSHA1 reader defaults absent optional arrays to [],
        // while modern v2 exports always include them. Do not turn an invalid
        // present-but-wrong-type field into an empty array.
        fun rows(name: String) = if (body.has(name)) body.getJSONArray(name)
            else {
                require(!bytes.copyOfRange(0, 8).contentEquals(magic)) {
                    "Missing required v2 field $name"
                }
                org.json.JSONArray()
            }
        val members = rows("familyMembers")
        val holdings = rows("holdings")
        val ledger = rows("ledgerEvents")
        val accounts = rows("accounts")
        val goals = rows("goals")
        val ownerIds = mutableSetOf<String>()
        var validOwners = true
        for (i in 0 until members.length()) {
            val member = members.getJSONObject(i)
            val id = member.optString("id").trim()
            if (id.isBlank() || !ownerIds.add(id)) validOwners = false
        }
        for (arr in listOf(holdings, ledger, accounts, goals)) {
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                val owner = row.optString("ownerId", row.optString("entityId")).trim()
                // Goals may be unowned in the frozen source. Other rows must
                // always resolve to a unique original family member.
                if ((owner.isBlank() && arr !== goals) ||
                    (owner.isNotBlank() && owner !in ownerIds)) validOwners = false
            }
        }
        return Inspection(sha256(bytes), storedEmail,
            members.length(), holdings.length(), ledger.length(), validOwners)
    }

    /**
     * Security-gated read-only inspection. The passphrase unlocks the archive;
     * account password, PAN/DOB and BOTH recovery answers are independently
     * checked. Still never commits or replaces any ledger row.
     * A masked-only PAN remains a weaker historical check and must not alone
     * establish device trust or authorize import.
     */
    @Synchronized
    fun inspectWithRecoveryProof(
        credentials: RestoreIdentityGate.Credentials
    ): Inspection {
        val inspection = inspect(credentials.email, credentials.password)
        require(inspection.ownersConsistent) { "Restore ownership invalid" }
        val bytes = StableStagedArchiveReader.read(staged.baseFile.toPath())
        // Restore review must bind to the exact archive inspected earlier.
        // A second instance or filesystem actor could otherwise replace the
        // atomic file between inspect() and recovery credential verification.
        require(sha256(bytes) == inspection.sha256) {
            "Staged archive changed during recovery inspection"
        }
        val plain = if (bytes.size >= 8 &&
            bytes.copyOfRange(0, 8).contentEquals(magic)) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(
                bytes, credentials.email, credentials.password)
        } else {
            val outer = legacyOuter(bytes)
            LegacyBackupEnvelopeReader.decodeKOSHA1(
                LegacyBackupEnvelopeReader.Kosha1(
                    outer.getString("magic"),
                legacyKdf(outer), legacyRounds(outer), outer.getString("saltB64"),
                    outer.getString("ivB64"), outer.getString("cipherB64")
                ), credentials.password)
        }
        val account = JSONObject(plain).getJSONObject("account")
        RestoreIdentityGate.verifyForNewDevice(
            RestoreIdentityGate.Account(
                account.getString("email"),
                account.getString("passwordSalt"),
                account.getString("passwordHash"),
                account.getInt("passwordIterations"),
                account.optString("panForStatements"),
                account.optString("panMasked"),
                account.optString("dateOfBirth"),
                account.getString("q1Id"),
                account.getString("q1Hash"),
                account.getString("q2Id"),
                account.getString("q2Hash")
            ), credentials
        )
        return inspection
    }

    /**
     * Read-only, fail-closed projection of the exact GCM-decrypted archive
     * AFTER strict account proof. The staged SHA binds this second read to
     * the previously inspected bytes, including across two AtomicFile reads.
     * Incomplete historical snapshots fail; no guessed ledger rows are made.
     *
     * NOT a restore/approval API: no database, Keystore key, replay claim or
     * destination file is modified. Caller cannot skip ownership review.
     */
    @Synchronized
    fun previewAuthenticatedV9(
        credentials: RestoreIdentityGate.Credentials
    ): RestoreV9Plan.Plan {
        val proof = inspectWithRecoveryProof(credentials)
        check(proof.ownersConsistent) { "Restore owner references are invalid" }
        val bytes = StableStagedArchiveReader.read(staged.baseFile.toPath())
        require(sha256(bytes) == proof.sha256) { "Staged restore changed after identity proof" }
        val plain = if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(magic)) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(bytes, credentials.email, credentials.password)
        } else {
            val outer = legacyOuter(bytes)
            LegacyBackupEnvelopeReader.decodeKOSHA1(
                LegacyBackupEnvelopeReader.Kosha1(
                    outer.getString("magic"),
                legacyKdf(outer), legacyRounds(outer), outer.getString("saltB64"),
                    outer.getString("ivB64"), outer.getString("cipherB64")
                ), credentials.password)
        }
        // The strict adapter requires genuine v2 schema fields in the SAME
        // decrypted JSON; older/partial archives remain read-only until a
        // separately reviewed lossless migration policy exists.
        return FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(plain)
    }

    // Frozen backup.ts unpackLegacyJson uses PBKDF2-SHA256/600000 when
    // optional legacy JSON metadata is absent (or rounds are zero).
    // Preserve an explicitly supplied non-default value for the strict
    // LegacyBackupEnvelopeReader to reject. Never infer financial rows.
    private fun legacyKdf(outer: JSONObject): String =
        if (outer.has("kdf")) outer.getString("kdf") else ""

    private fun legacyRounds(outer: JSONObject): Int =
        if (outer.has("iterations")) outer.getInt("iterations") else 0

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
