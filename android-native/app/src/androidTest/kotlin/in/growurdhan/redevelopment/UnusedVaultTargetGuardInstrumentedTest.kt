package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-only path/lock preflight, with private synthetic filesystem
 * artifacts. Does not execute a real restore, handle real financial bytes,
 * or grant a credential/session.
 */
@RunWith(AndroidJUnit4::class)
class UnusedVaultTargetGuardInstrumentedTest {
    private fun sandbox(): Context {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(original.cacheDir, "unused-vault-guard-" + UUID.randomUUID())
            .also { assertTrue(it.mkdirs()) }
        return object : ContextWrapper(original) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }
    private fun staged(ctx: Context) =
        File(ctx.noBackupFilesDir, "restore-pending.v1.kosha")

    @Test fun cleanNewDeviceAllowsEitherModeButStagedArchiveOnlyAllowsRestore() {
        val ctx = sandbox()
        UnusedVaultTargetGuard.requireUnused(ctx, false)
        UnusedVaultTargetGuard.requireUnused(ctx, true)
        val bytes = ByteArray(80) { it.toByte() }
        staged(ctx).writeBytes(bytes)
        UnusedVaultTargetGuard.requireUnused(ctx, true)
        assertThrows(IllegalStateException::class.java) {
            UnusedVaultTargetGuard.requireUnused(ctx, false)
        }
        assertArrayEquals(bytes, staged(ctx).readBytes())
    }

    @Test fun danglingSymlinkIsNotAnEmptyVaultOrPermissibleKey() {
        for (target in listOf("database", "wal", "journal", "shm",
            "key", "key-new", "key-bak", "legacy-files", "legacy-nobackup")) {
            val ctx = sandbox()
            val database = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
            val key = File(ctx.noBackupFilesDir, "vault-key.v1")
            val path = when (target) {
                "database" -> database
                "wal" -> File(database.path + "-wal")
                "journal" -> File(database.path + "-journal")
                "shm" -> File(database.path + "-shm")
                "key" -> key
                "key-new" -> File(key.path + ".new")
                "key-bak" -> File(key.path + ".bak")
                "legacy-files" -> File(ctx.filesDir, "vault.enc")
                else -> File(ctx.noBackupFilesDir, "vault.enc")
            }
            val unavailable = File(path.parentFile, "absent-original-$target")
            Files.createSymbolicLink(path.toPath(), unavailable.toPath())
            assertFalse(path.exists()) // File.exists follows a dangling link
            assertThrows(IllegalStateException::class.java) {
                UnusedVaultTargetGuard.requireUnused(ctx, true)
            }
            assertTrue(Files.isSymbolicLink(path.toPath()))
            assertFalse(Files.exists(unavailable.toPath(), LinkOption.NOFOLLOW_LINKS))
        }
    }

    @Test fun filesPickedBeforeNewVaultWasCreatedCannotBeStagedAfterwards() {
        val ctx = sandbox()
        NativeRestoreFlow(ctx).requireEmptyTarget()
        val nativeVault = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
        val original = ByteArray(128) { (it + 17).toByte() }
        nativeVault.writeBytes(original) // another actor acted AFTER early preview
        val syntheticEnvelope = "GUDVLT01".toByteArray(Charsets.US_ASCII) +
            ByteArray(72) { 0x42.toByte() }
        assertThrows(IllegalStateException::class.java) {
            LockedRestoreStaging(ctx).stageEncryptedArchiveOnce(syntheticEnvelope)
        }
        assertFalse(staged(ctx).exists())
        assertArrayEquals(original, nativeVault.readBytes())
        assertFalse(File(ctx.noBackupFilesDir, "restore-claims").exists())
    }

    @Test fun stagedSymlinkAndInterruptedAtomicArtifactsArePreserved() {
        for (name in listOf("restore-pending.v1.kosha",
            "restore-pending.v1.kosha.new", "restore-pending.v1.kosha.bak")) {
            val ctx = sandbox()
            val evidence = File(ctx.noBackupFilesDir, name)
            val missing = File(ctx.noBackupFilesDir, "never-created")
            Files.createSymbolicLink(evidence.toPath(), missing.toPath())
            assertThrows(IllegalStateException::class.java) {
                UnusedVaultTargetGuard.requireUnused(ctx, true)
            }
            assertTrue(Files.isSymbolicLink(evidence.toPath()))
            assertFalse(File(ctx.noBackupFilesDir, "vault-key.v1").exists())
        }
    }

    @Test fun freshVaultRefusesDanglingDatabaseLinkBeforeKeyCreation() {
        val ctx = sandbox()
        val target = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
        val missing = File(target.parentFile, "nonexistent-original")
        Files.createSymbolicLink(target.toPath(), missing.toPath())
        assertThrows(IllegalStateException::class.java) {
            EncryptedVaultStore(ctx).createFresh(legacyVaultPresent = false).close()
        }
        assertTrue(Files.isSymbolicLink(target.toPath()))
        assertFalse(File(ctx.noBackupFilesDir, "vault-key.v1").exists())
    }
    @Test fun corruptedKeySidecarIsNeverConsumedOrReconciledByAtomicRead() {
        for (suffix in listOf(".bak", ".new")) {
            val ctx = sandbox()
            val key = File(ctx.noBackupFilesDir, "vault-key.v1")
            val synthetic = "GUDKEY01".toByteArray(Charsets.US_ASCII) + ByteArray(60)
            key.writeBytes(synthetic)
            val sidecar = File(key.path + suffix)
            val evidence = byteArrayOf(0x15, 0x27, 0x39)
            sidecar.writeBytes(evidence)
            assertThrows(IllegalStateException::class.java) {
                KeystoreWrappedVaultKey(ctx).unwrapExisting()
            }
            assertArrayEquals(synthetic, key.readBytes())
            assertArrayEquals(evidence, sidecar.readBytes())
        }
    }

    @Test fun wrappedKeySymlinkIsRefusedWithoutOpeningTheLinkTarget() {
        val ctx = sandbox()
        val key = File(ctx.noBackupFilesDir, "vault-key.v1")
        val external = File(ctx.noBackupFilesDir, "not-a-key")
        Files.createSymbolicLink(key.toPath(), external.toPath())
        assertTrue(KeystoreWrappedVaultKey(ctx).hasExistingEnvelope())
        assertThrows(IllegalStateException::class.java) {
            KeystoreWrappedVaultKey(ctx).unwrapExisting()
        }
        assertTrue(Files.isSymbolicLink(key.toPath()))
        assertFalse(external.exists())
    }

    @Test fun linkedExistingVaultCannotBeOpenedAndLinkedCandidateCannotBeCreated() {
        val ctx = sandbox()
        val live = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
        val elsewhere = File(live.parentFile, "not-an-existing-vault")
        Files.createSymbolicLink(live.toPath(), elsewhere.toPath())
        assertThrows(IllegalStateException::class.java) {
            EncryptedVaultStore(ctx).openExisting().close()
        }
        assertTrue(Files.isSymbolicLink(live.toPath()))

        val candidate = File(live.parentFile,
            "growurdhan-v9.sqlcipher.restore-11111111-2222-3333-4444-555555555555")
        val other = File(live.parentFile, "not-an-existing-candidate")
        Files.createSymbolicLink(candidate.toPath(), other.toPath())
        assertThrows(IllegalStateException::class.java) {
            EncryptedVaultStore(ctx).createNewRestoreCandidate(
                candidate, ByteArray(32)).close()
        }
        assertTrue(Files.isSymbolicLink(candidate.toPath()))
    }

}
