package `in`.growurdhan.database

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Durable fail-closed anti-replay claim for a previously authenticated archive.
 *
 * claimOnce() MUST be called only AFTER GCM authentication, owner recovery and typed
 * preflight have succeeded, and BEFORE creating any SQLCipher target. Claims are
 * permanent even if a process dies before DB commit: recovery requires explicit
 * owner-reviewed forensic cleanup, never a silent retry/reset.
 *
 * The caller MUST use a no-backup, app-private directory and ensure no untrusted
 * actor can replace its directory; this class neither decrypts nor imports rows.
 */
class RestoreOnceJournal(private val directory: Path) {
    private val sha = Regex("[0-9a-f]{64}")
    data class Claim(val fingerprint: String)

    fun claimOnce(fingerprint: String): Claim {
        require(sha.matches(fingerprint)) { "Invalid restore fingerprint" }
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(directory)) { "Missing secure restore journal" }
        val mark = directory.resolve("restore-" + fingerprint + ".claim")
        try {
            FileChannel.open(mark, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val bytes = ("GUD_RESTORE_CLAIM_V1\n" + fingerprint + "\n").toByteArray(Charsets.US_ASCII)
                val buf = ByteBuffer.wrap(bytes)
                while (buf.hasRemaining()) channel.write(buf)
                channel.force(true)
            }
        } catch (e: FileAlreadyExistsException) {
            throw IllegalStateException("Archive fingerprint already claimed; manual review required", e)
        }
        // Force the directory entry as well as the file. If the filesystem
        // refuses directory fsync, remain fail-closed and leave the claim.
        FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        // No claim deletion or automatic rollback API: a crash must never
        // silently clear the replay barrier.
        return Claim(fingerprint)
    }

    fun hasClaim(fingerprint: String): Boolean {
        require(sha.matches(fingerprint))
        return Files.exists(directory.resolve("restore-" + fingerprint + ".claim"),
            LinkOption.NOFOLLOW_LINKS)
    }
}
