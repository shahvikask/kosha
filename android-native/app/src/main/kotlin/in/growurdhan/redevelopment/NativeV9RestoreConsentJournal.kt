package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeRegistrationProof
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * App-private immutable acceptance event for a newly restored native device.
 * The archive's historic setting remains losslessly unchanged; a new consent
 * is bound to the newly authenticated exact source fingerprint separately.
 * No PAN, owner answer, password, account identifier or financial data stored.
 * This receipt is NOT authorization to bypass current UI or device admission.
 */
internal object NativeV9RestoreConsentJournal {
    private val fingerprint = Regex("[0-9a-f]{64}")
    private val noFollow = LinkOption.NOFOLLOW_LINKS
    private fun filename(sha: String): String {
        require(fingerprint.matches(sha)) { "Invalid recovery fingerprint" }
        return "restore-" + sha + ".terms"
    }
    private fun bytes(sha: String, consent: NativeRegistrationProof.Consent): ByteArray {
        NativeRegistrationProof.requireValidConsent(consent)
        return ("GUD_NATIVE_RESTORE_TERMS_V1\n" + sha + "\n" + consent.version +
            "\n" + consent.textId + "\n" + consent.acceptedAt + "\n")
            .toByteArray(Charsets.US_ASCII)
    }
    private fun dir(noBackup: Path): Path {
        require(Files.isDirectory(noBackup, noFollow) &&
            !Files.isSymbolicLink(noBackup)) { "Unsafe private restore directory" }
        val d = noBackup.resolve("restore-terms")
        if (!Files.exists(d, noFollow)) Files.createDirectory(d)
        require(Files.isDirectory(d, noFollow) && !Files.isSymbolicLink(d)) {
            "Unsafe restore acceptance directory"
        }
        return d
    }
    /** Caller holds VaultLifecycleLock and has already made a durable claim. */
    fun recordOnce(noBackup: Path, sha: String,
        consent: NativeRegistrationProof.Consent) {
        val d = dir(noBackup)
        val file = d.resolve(filename(sha))
        val data = bytes(sha, consent)
        try {
            FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, noFollow).use { channel ->
                val buffer = ByteBuffer.wrap(data)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        } catch (e: FileAlreadyExistsException) {
            throw IllegalStateException("Recovery Terms event already recorded", e)
        } finally { data.fill(0) }
        // Preserve even a partial journal on any fsync failure; no auto cleanup.
        FileChannel.open(d, StandardOpenOption.READ).use { it.force(true) }
    }
    /** Read-only, no implicit AtomicFile reconciliation or journal removal. */
    fun requireExact(noBackup: Path, sha: String,
        consent: NativeRegistrationProof.Consent) {
        val expected = bytes(sha, consent)
        try {
            val parent = noBackup.resolve("restore-terms")
            require(Files.isDirectory(parent, noFollow) &&
                !Files.isSymbolicLink(parent))
            val file = parent.resolve(filename(sha))
            require(Files.isRegularFile(file, noFollow) &&
                !Files.isSymbolicLink(file) &&
                Files.size(file) == expected.size.toLong() &&
                Files.readAllBytes(file).contentEquals(expected)) {
                "Recovery Terms event missing or changed"
            }
        } finally { expected.fill(0) }
    }

    /**
     * Read the original, immutable new-device acceptance timestamp after a
     * process restart. Only owner-verified forensic review may call this.
     * Current Terms version and exact bytes must still verify.
     */
    fun readCurrent(noBackup: Path, sha: String): NativeRegistrationProof.Consent {
        val parent = noBackup.resolve("restore-terms")
        require(Files.isDirectory(parent, noFollow) &&
            !Files.isSymbolicLink(parent)) { "Missing protected acceptance evidence" }
        val file = parent.resolve(filename(sha))
        require(Files.isRegularFile(file, noFollow) &&
            !Files.isSymbolicLink(file) && Files.size(file) in 90L..512L) {
            "Missing or oversized native acceptance evidence"
        }
        val lines = Files.readAllBytes(file).toString(Charsets.US_ASCII).split('\n')
        require(lines.size == 6 && lines.last().isEmpty() &&
            lines[0] == "GUD_NATIVE_RESTORE_TERMS_V1" && lines[1] == sha) {
            "Invalid native acceptance receipt"
        }
        val accepted = NativeRegistrationProof.Consent(
            lines[2], lines[3], lines[4], scrolledToEnd = true)
        requireExact(noBackup, sha, accepted)
        return accepted
    }

}
