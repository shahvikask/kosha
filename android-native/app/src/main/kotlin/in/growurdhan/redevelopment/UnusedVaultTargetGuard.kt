package `in`.growurdhan.redevelopment

import android.content.Context
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * One read-only fail-closed predicate for a genuinely empty native vault.
 *
 * File.exists() follows symlinks, so a dangling symlink to a vault, key,
 * journal or legacy database incorrectly looks "empty". These objects must
 * not be overwritten or used to create a blank vault. The caller holds
 * VaultLifecycleLock for all creation/staging/publication writes and repeats
 * this check AFTER user-controlled SAF access, never only before file picking.
 *
 * This verifier NEVER deletes, renames or attempts an AtomicFile recovery.
 */
internal object UnusedVaultTargetGuard {
    private val noFollow = LinkOption.NOFOLLOW_LINKS

    private fun requireSafeDirectory(path: Path, required: Boolean) {
        if (!Files.exists(path, noFollow)) {
            check(!required) { "Protected native vault directory is missing" }
            return
        }
        check(Files.isDirectory(path, noFollow) && !Files.isSymbolicLink(path)) {
            "Protected native vault directory cannot be a link or non-directory"
        }
    }

    private fun requireAbsent(path: Path) {
        check(!Files.exists(path, noFollow)) {
            "Existing, linked or incomplete native vault artifact; owner review required"
        }
    }

    /** No permission to clear or replace any existing native/legacy artifact. */
    fun requireUnused(context: Context, allowAlreadyStagedArchive: Boolean) {
        val protectedDir = context.noBackupFilesDir.toPath()
        val filesDir = context.filesDir.toPath()
        val database = context.getDatabasePath("growurdhan-v9.sqlcipher").toPath()
        val databaseDir = database.parent
            ?: error("Missing native SQLCipher directory")

        requireSafeDirectory(protectedDir, required = true)
        requireSafeDirectory(filesDir, required = true)
        requireSafeDirectory(databaseDir, required = false)

        for (suffix in listOf("", "-journal", "-wal", "-shm")) {
            requireAbsent(database.resolveSibling(database.fileName.toString() + suffix))
        }
        val liveKey = protectedDir.resolve("vault-key.v1")
        for (suffix in listOf("", ".new", ".bak")) {
            requireAbsent(liveKey.resolveSibling(liveKey.fileName.toString() + suffix))
        }
        requireAbsent(filesDir.resolve("vault.enc"))
        requireAbsent(protectedDir.resolve("vault.enc"))

        val pending = protectedDir.resolve("restore-pending.v1.kosha")
        for (suffix in listOf(".new", ".bak")) {
            requireAbsent(pending.resolveSibling(pending.fileName.toString() + suffix))
        }
        if (allowAlreadyStagedArchive) {
            // A legitimate previously staged encrypted file is the ONLY
            // allowed artifact and must be read later through a bounded,
            // immutable, symlink-safe forensic reader.
            if (Files.exists(pending, noFollow)) {
                check(Files.isRegularFile(pending, noFollow) &&
                    !Files.isSymbolicLink(pending)) {
                    "Unsafe or linked staged backup"
                }
            }
        } else requireAbsent(pending)
        RestoreArtifactGuard.requireNoAbandonedRestore(context)
    }
}
