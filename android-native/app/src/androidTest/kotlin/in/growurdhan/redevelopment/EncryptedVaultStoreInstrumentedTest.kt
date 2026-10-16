package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M24 real Android SQLCipher/Keystore acceptance probes.
 * Not yet run on emulator or real hardware. Each probe uses an isolated
 * temporary Context; no production database is opened or deleted.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedVaultStoreInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m24-android-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }

    private val preserve = DatabaseErrorHandler {
        throw IllegalStateException("Test corruption; never delete source")
    }

    private fun scalar(db: SQLiteDatabase, query: String): Long =
        db.rawQuery(query, null).use { cur ->
            assertTrue(cur.moveToFirst())
            cur.getLong(0)
        }

    @Test fun encryptedV9PersistsAcrossRestartAndRejectsBrokenSchemaMarkers() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        store.createFresh(false).use { db ->
            assertEquals(9L, scalar(db, "PRAGMA user_version"))
            assertEquals(18L, scalar(db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"))
            assertEquals(3L, scalar(db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger'"))
        }
        val bytes = ctx.getDatabasePath("growurdhan-v9.sqlcipher").readBytes()
        assertFalse(bytes.copyOfRange(0, 16)
            .contentEquals("SQLite format 3\u0000".toByteArray(Charsets.UTF_8)))
        store.openExisting().use { db ->
            assertEquals(9L, scalar(db, "PRAGMA user_version"))
            db.execSQL("PRAGMA user_version=8")
        }
        assertThrows(Exception::class.java) { store.openExisting().close() }
        val key = KeystoreWrappedVaultKey(ctx).unwrapExisting()
        try {
            val db = SQLiteDatabase.openDatabase(
                ctx.getDatabasePath("growurdhan-v9.sqlcipher").absolutePath,
                key, null, SQLiteDatabase.OPEN_READWRITE, preserve, null)
            db.use {
                assertEquals(8L, scalar(it, "PRAGMA user_version"))
                it.execSQL("PRAGMA user_version=9")
            }
        } finally { key.fill(0) }
        store.openExisting().use {
            assertEquals(9L, scalar(it, "PRAGMA user_version"))
        }
    }

    @Test fun ledgerIsAppendOnlyAndInvalidAccountOwnershipIsRejected() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        store.createFresh(false).use { db ->
            db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,created_at) " +
                "VALUES ('synthetic-A','INDIVIDUAL','Synthetic','2026-10-16')")
            db.execSQL("INSERT INTO instrument(instrument_id,name,category,created_at) " +
                "VALUES ('synthetic-I','Test','EQUITY','2026-10-16')")
            db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id," +
                "current_price_minor,tags_json,created_at) VALUES " +
                "('synthetic-H','synthetic-I','synthetic-A',999," +
                "'{\"opaque\":true}','2026-10-16')")
            db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
                "holding_name,event_type,trade_date,quantity_scaled,unit_price_minor," +
                "total_amount_minor,charges_minor,entered_at) VALUES " +
                "('synthetic-E','synthetic-A','synthetic-H','Synthetic'," +
                "'OPENING_BALANCE','2026-10-16',1234567890123,100,9876543210123,0,'2026-10-16')")
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE ledger_event SET notes='tampered' WHERE ledger_event_id='synthetic-E'")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM ledger_event WHERE ledger_event_id='synthetic-E'")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO account(account_id,entity_id,account_type,custodian,created_at) " +
                    "VALUES ('bad','missing-owner','BROKER','Synthetic','2026-10-16')")
            }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
        }
        store.openExisting().use { db ->
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
            assertEquals(1234567890123L,
                scalar(db, "SELECT quantity_scaled FROM ledger_event"))
        }
    }

    @Test fun wrongKeyAndCorruptCopyFailWithoutResettingLiveCiphertext() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        store.createFresh(false).close()
        val source = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
        val original = Files.readAllBytes(source.toPath())
        val key = KeystoreWrappedVaultKey(ctx).unwrapExisting()
        try {
            val badKey = key.clone().also {
                it[0] = (it[0].toInt() xor 1).toByte()
            }
            try {
                assertThrows(Exception::class.java) {
                    SQLiteDatabase.openDatabase(source.absolutePath, badKey, null,
                        SQLiteDatabase.OPEN_READWRITE, preserve, null).use { db ->
                        db.rawQuery("SELECT name FROM sqlite_master", null).use { it.count }
                    }
                }
            } finally { badKey.fill(0) }
            val corrupted = File(source.parentFile, "corrupted-copy.db")
            corrupted.writeBytes(original.copyOf().also {
                it[0] = (it[0].toInt() xor 1).toByte()
            })
            assertThrows(Exception::class.java) {
                SQLiteDatabase.openDatabase(corrupted.absolutePath, key, null,
                    SQLiteDatabase.OPEN_READWRITE, null).use { db ->
                    db.rawQuery("PRAGMA integrity_check", null).use { it.count }
                }
            }
            assertArrayEquals(original, Files.readAllBytes(source.toPath()))
        } finally { key.fill(0) }
        store.openExisting().use { assertEquals(9L, scalar(it, "PRAGMA user_version")) }
    }
}
