package `in`.growurdhan.redevelopment

import android.content.Context
import android.net.Uri
import `in`.growurdhan.database.RestoreIdentityGate
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * New-device only. No legacy bytes, PAN, answers or password are persisted in
 * Compose saved state or logs. Existing vaults must never be overwritten.
 */
internal class NativeRestoreFlow(private val context: Context) {
    data class Preview(val sha256: String, val members: Int, val holdings: Int, val ledger: Int)
    private val staging get() = LockedRestoreStaging(context)

    fun requireEmptyTarget() {
        val db = context.getDatabasePath("growurdhan-v9.sqlcipher")
        val key = File(context.noBackupFilesDir, "vault-key.v1")
        check(!db.exists() && !File(db.path + "-wal").exists() &&
            !File(db.path + "-shm").exists() && !File(db.path + "-journal").exists() &&
            !key.exists() && !File(key.path + ".bak").exists() &&
            !File(context.filesDir, "vault.enc").exists() &&
            !File(context.noBackupFilesDir, "vault.enc").exists()) {
            "Restore is available only on a new empty device vault"
        }
        RestoreArtifactGuard.requireNoAbandonedRestore(context)
    }

    fun stage(uri: Uri): String {
        requireEmptyTarget()
        val bytes = context.contentResolver.openInputStream(uri)?.use { source ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val n = source.read(chunk)
                if (n == -1) break
                require(out.size() + n <= 64 * 1024 * 1024) { "Archive too large" }
                out.write(chunk, 0, n)
            }
            out.toByteArray()
        } ?: error("Cannot read encrypted archive")
        return staging.stageEncryptedArchiveOnce(bytes).sha256
    }

    fun preview(proof: RestoreIdentityGate.Credentials): Preview {
        requireEmptyTarget()
        val plan = staging.previewAuthenticatedV9(proof)
        return Preview(plan.originalJsonSha256, plan.rows.count { it.table == "entity" },
            plan.rows.count { it.table == "holding" },
            plan.rows.count { it.table == "ledger_event" })
    }

    fun publish(proof: RestoreIdentityGate.Credentials, preview: Preview) {
        requireEmptyTarget()
        val fresh = staging.previewAuthenticatedV9(proof)
        check(fresh.originalJsonSha256 == preview.sha256) {
            "Authenticated restore preview changed; start review again"
        }
        ApprovedNewVaultRestore(context).restoreFromStagedNewVault(proof, true)
    }
}
