package `in`.growurdhan.database

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
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
    /**
     * Android publication variant where hard links are forbidden by the
     * application sandbox's filesystem/SELinux policy.
     *
     * 1. CREATE_NEW the final name atomically (fails if ANY existing vault,
     *    symlink, or abandoned reservation is present).
     * 2. Under the caller's EXCLUSIVE vault lifecycle lock, atomically rename
     *    the verified, fsynced candidate over ONLY our freshly-created EMPTY
     *    reservation. No existing financial database is ever replaced.
     * 3. Preserve the empty reservation and original candidate on any failure
     *    for deliberate owner-reviewed crash recovery. No cleanup fallback.
     *
     * This relies on same-directory ATOMIC_MOVE; if the filesystem cannot
     * implement an atomic rename it MUST fail rather than copying bytes.
     * All vault creation paths in the app must honor VaultLifecycleLock.
     */
    fun publishReservedAtomic(closedCandidate: Path, finalVault: Path) {
        val source = closedCandidate.toAbsolutePath().normalize()
        val target = finalVault.toAbsolutePath().normalize()
        require(source != target && source.parent == target.parent) {
            "Restore candidate and live path must share a filesystem directory"
        }
        val parent = source.parent ?: error("Missing encrypted vault directory")
        require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent)) {
            "Unsafe encrypted vault publication directory"
        }
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(source) && Files.size(source) > 0L) {
            "Missing or unsafe closed encrypted restore candidate"
        }
        for (name in listOf(source.fileName.toString(), target.fileName.toString())) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                require(!Files.exists(parent.resolve(name + suffix), LinkOption.NOFOLLOW_LINKS)) {
                    "Restore has journal or WAL sidecar; close and checkpoint first"
                }
            }
        }
        // O_EXCL-equivalent: this must FAIL when ANY final name already exists.
        // An empty placeholder is an intentional crash-recovery tombstone.
        Files.createFile(target)
        FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
        FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
            it.force(true)
        }
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(target) == 0L) { "Exclusive destination reservation changed" }
        // Never move without ATOMIC_MOVE. REPLACE_EXISTING applies ONLY to the
        // empty placeholder that this call just created using CREATE_NEW.
        // An exception leaves the tombstone in place; no silent reset/retry.
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
    }

}
