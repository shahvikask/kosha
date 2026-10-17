package `in`.growurdhan.database

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Non-clobbering publication of a CLOSED, authenticated and fully verified
 * temporary SQLCipher file into its final path, on the same filesystem.
 *
 * Preconditions MUST be enforced by its caller: authenticated snapshot, strict
 * identity proof, typed lossless import committed, cipher_integrity_check,
 * foreign_key_check, a durable replay claim, exclusive lifecycle lock, and
 * durable Keystore-wrapped target key. This is not a restore orchestrator.
 *
 * Unlike Files.move(ATOMIC_MOVE), whose behavior with an existing target is
 * implementation-specific, createLink() fails atomically when a name exists.
 * If hard links are unsupported, fail closed; NEVER fall back to overwrite.
 */
object EncryptedRestorePublisher {
    fun publishExclusive(closedCandidate: Path, finalVault: Path) {
        val source = closedCandidate.toAbsolutePath().normalize()
        val target = finalVault.toAbsolutePath().normalize()
        require(source != target && source.parent == target.parent) {
            "Restore target must be a distinct name in the same directory"
        }
        val parent = source.parent ?: error("Missing encrypted vault directory")
        require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent)) { "Unsafe encrypted vault directory" }
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(source) && Files.size(source) > 0L) {
            "Missing or unsafe verified encrypted candidate"
        }
        // SQLite WAL/journal sidecars may still contain committed data; refuse
        // publication until caller checkpoints and CLOSES the encrypted DB.
        for (name in listOf(source.fileName.toString(), target.fileName.toString())) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                require(!Files.exists(parent.resolve(name + suffix), LinkOption.NOFOLLOW_LINKS)) {
                    "Encrypted vault has an uncheckpointed journal or WAL sidecar"
                }
            }
        }
        check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            "A live encrypted vault already exists; never replace it"
        }
        FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
            it.force(true)
        }
        // Hard-link creation is the atomic, no-replace publication point. A
        // concurrent publisher can never replace existing target bytes.
        Files.createLink(target, source)
        // If directory fsync fails, leave target intact: manual crash recovery
        // must inspect its existing bytes, never recreate or silently delete.
        FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
        Files.delete(source)
        FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
    }
}
