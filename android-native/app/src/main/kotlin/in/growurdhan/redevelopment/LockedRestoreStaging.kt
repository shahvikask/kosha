package `in`.growurdhan.redevelopment

import android.content.Context
import android.util.AtomicFile
import `in`.growurdhan.database.LegacyBackupEnvelopeReader
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
internal class LockedRestoreStaging(context: Context) {
    private val staged = AtomicFile(File(context.noBackupFilesDir, "restore-pending.v1.kosha"))
    private val magic = "GUDVLT01".toByteArray(Charsets.US_ASCII)
    private val maxBytes = 64 * 1024 * 1024

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
    fun stageEncryptedArchiveOnce(envelope: ByteArray): QuarantineReceipt {
        require(envelope.size in 56..maxBytes) { "Unsupported or oversized encrypted archive" }
        val binary = envelope.copyOfRange(0, 8).contentEquals(magic)
        if (!binary) {
            // Unlocked JSON must never enter the restore quarantine.
            val outer = try { JSONObject(String(envelope, Charsets.UTF_8)) }
                catch (_: Exception) { throw IllegalArgumentException("Unsupported backup envelope") }
            require(outer.optString("magic") == "KOSHA1" &&
                outer.has("cipherB64")) { "Unencrypted or unknown backup refused" }
        }
        check(!staged.baseFile.exists() &&
            !File(staged.baseFile.path + ".bak").exists()) {
            "A restore is already staged; explicit review is required"
        }
        val out = staged.startWrite()
        try {
            out.write(envelope)
            staged.finishWrite(out)
        } catch (failure: Exception) {
            staged.failWrite(out)
            throw failure
        }
        return QuarantineReceipt(sha256(envelope), envelope.size)
    }

    /**
     * No state mutation. Consumer MUST implement complete account recovery,
     * ownership review, replay policy and transactional SQLCipher import before
     * using this data. Raw unknown JSON fields are never normalized or dropped.
     */
    @Synchronized
    fun inspect(email: String, password: String): Inspection {
        check(staged.baseFile.exists() ||
            File(staged.baseFile.path + ".bak").exists()) { "No pending encrypted restore" }
        // AtomicFile may recover a .bak file even if baseFile is short or
        // absent. Limit the read itself rather than trusting baseFile.length().
        val bytes = staged.openRead().use { source ->
            val bounded = source.readNBytes(maxBytes + 1)
            require(bounded.size in 56..maxBytes) { "Oversized or truncated staged restore" }
            bounded
        }
        val plain = if (bytes.copyOfRange(0, 8).contentEquals(magic)) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(bytes, email, password)
        } else {
            val outer = JSONObject(String(bytes, Charsets.UTF_8))
            val blob = LegacyBackupEnvelopeReader.Kosha1(
                outer.getString("magic"), outer.getString("kdf"),
                outer.getInt("iterations"), outer.getString("saltB64"),
                outer.getString("ivB64"), outer.getString("cipherB64")
            )
            LegacyBackupEnvelopeReader.decodeKOSHA1(blob, password)
        }
        val body = JSONObject(plain)
        require(body.getString("kind") == "GUDVAULT" && body.has("account")) {
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
