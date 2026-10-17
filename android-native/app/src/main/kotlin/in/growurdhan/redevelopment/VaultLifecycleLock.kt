package `in`.growurdhan.redevelopment

import android.content.Context
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/**
 * Process-independent advisory lock for the entire vault creation/restore
 * publication critical section. The lockfile is durable and is NEVER removed.
 * Every native vault creator must participate in this same lock.
 */
internal object VaultLifecycleLock {
    fun <T> exclusively(context: Context, action: () -> T): T {
        val dir = context.noBackupFilesDir.toPath()
        require(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(dir)) { "Unsafe vault lifecycle directory" }
        val lockPath = dir.resolve("vault-lifecycle.lock")
        FileChannel.open(lockPath, StandardOpenOption.CREATE,
            StandardOpenOption.READ, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                throw IllegalStateException("Vault lifecycle operation already active", e)
            }
            check(lock != null) { "Vault lifecycle operation already active" }
            lock.use { return action() }
        }
    }
}
