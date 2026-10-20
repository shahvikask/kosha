package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.RestoreV9Plan
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genuine SQLCipher database with fake bookkeeping rows only. Exercises the
 * same exact-value comparator used BEFORE restore publication and during
 * interrupted-restore forensic review; does not publish or restore a vault.
 */
@RunWith(AndroidJUnit4::class)
class RestoredRowsVerifierInstrumentedTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "restore-value-proof-" + UUID.randomUUID())
            .also { it.mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }

    @Test fun equalTypedEncryptedRowsMatchOriginalRestorePlan() {
        EncryptedVaultStore(isolated()).createFresh(false).use { db ->
            db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>("synthetic.setting", "recorded original amount 00012"))
            val plan = RestoreV9Plan.Plan(
                listOf(RestoreV9Plan.Row("setting", listOf("key", "value"),
                    listOf("synthetic.setting", "recorded original amount 00012"))),
                "a".repeat(64))
            assertEquals(1, RestoredRowsVerifier.verifyExactRows(db, plan))
            // Changing a single financial value preserves table row counts
            // but must prevent live publication.
            db.execSQL("UPDATE setting SET value='recorded original amount 12' " +
                "WHERE key='synthetic.setting'")
            assertThrows(IllegalArgumentException::class.java) {
                RestoredRowsVerifier.verifyExactRows(db, plan)
            }
        }
    }

    @Test fun extraOrMissingTypedRowsNeverPassJustBecauseCountsLookPlausible() {
        EncryptedVaultStore(isolated()).createFresh(false).use { db ->
            db.execSQL("INSERT INTO setting(key,value) VALUES('one','one')")
            val plan = RestoreV9Plan.Plan(
                listOf(RestoreV9Plan.Row("setting", listOf("key", "value"),
                    listOf("one", "one"))), "b".repeat(64))
            db.execSQL("INSERT INTO setting(key,value) VALUES('extra','other')")
            assertThrows(IllegalArgumentException::class.java) {
                RestoredRowsVerifier.verifyExactRows(db, plan)
            }
            db.execSQL("DELETE FROM setting WHERE key='extra'")
            assertEquals(1, RestoredRowsVerifier.verifyExactRows(db, plan))
            db.execSQL("DELETE FROM setting WHERE key='one'")
            assertThrows(IllegalArgumentException::class.java) {
                RestoredRowsVerifier.verifyExactRows(db, plan)
            }
        }
    }

    @Test fun unexpectedTableOrDuplicatedColumnContractIsRejected() {
        EncryptedVaultStore(isolated()).createFresh(false).use { db ->
            val foreign = RestoreV9Plan.Plan(listOf(RestoreV9Plan.Row(
                "sqlite_master", listOf("name"), listOf("setting"))), "c".repeat(64))
            assertThrows(IllegalArgumentException::class.java) {
                RestoredRowsVerifier.verifyExactRows(db, foreign)
            }
            val duplicate = RestoreV9Plan.Plan(listOf(RestoreV9Plan.Row(
                "setting", listOf("key", "key"), listOf("one", "one"))),
                "d".repeat(64))
            assertThrows(IllegalArgumentException::class.java) {
                RestoredRowsVerifier.verifyExactRows(db, duplicate)
            }
        }
    }
}
