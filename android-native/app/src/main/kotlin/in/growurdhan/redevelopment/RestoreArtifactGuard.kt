package `in`.growurdhan.redevelopment

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Read-only crash-state guard shared by fresh-vault creation and new-device
 * restoration. A previous restore may have claimed a source and crashed before
 * publication; silently importing a different archive or creating empty books
 * would abandon financial data and potentially strand its wrapped key.
 *
 * Never delete any candidate, claim or reservation here. An owner-reviewed
 * recovery procedure must identify and reconcile those artifacts separately.
 * Call only UNDER VaultLifecycleLock before creating keys or SQLCipher files.
 */
internal object RestoreArtifactGuard {
    fun requireNoAbandonedRestore(context: Context) {
        val noBackup = context.noBackupFilesDir
        val dbDir = context.getDatabasePath("growurdhan-v9.sqlcipher").parentFile
            ?: error("Missing protected vault directory")
        val claims = File(noBackup, "restore-claims")
        check(!File(noBackup, "restore-pending.v1.kosha.new").exists() &&
            !File(noBackup, "vault-key.v1.new").exists() &&
            !File(noBackup, "restore-pending.v1.kosha.bak").exists() &&
            !File(noBackup, "vault-key.v1.bak").exists()) {
            "Interrupted AtomicFile encrypted backup or key write; owner review required"
        }

        fun filesIn(directory: File): Array<File> {
            if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return emptyArray()
            check(Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(directory.toPath())) {
                "Unsafe restore recovery directory: owner review required"
            }
            return directory.listFiles()
                ?: throw IllegalStateException("Unreadable restore recovery directory")
        }

        check(filesIn(claims).isEmpty()) {
            "Previous restore claim exists; owner-reviewed crash recovery required"
        }
        check(filesIn(noBackup).none { it.name.startsWith("vault-key.v1.restore-") }) {
            "Orphaned restore key envelope exists; owner-reviewed recovery required"
        }
        check(filesIn(dbDir).none { it.name.startsWith("growurdhan-v9.sqlcipher.restore-") }) {
            "Orphaned encrypted candidate exists; owner-reviewed recovery required"
        }
    }
}
