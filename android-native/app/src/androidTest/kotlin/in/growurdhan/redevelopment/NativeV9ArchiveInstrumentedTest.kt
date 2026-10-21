package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.NativeV9Archive
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test

/** Real SQLCipher/Keystore synthetic-only native archive experiments.
 * Does NOT open/publish a live user vault or approve cross-device recovery.
 */
class NativeV9ArchiveInstrumentedTest {
    private val email = "native@example.invalid"
    private val pass = "Synthetic separate archive password".toCharArray()
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "native-v9-archive-" + UUID.randomUUID())
        root.mkdirs()
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/" + name).also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }
    private fun candidate(ctx: Context, store: EncryptedVaultStore,
        body: (SQLiteDatabase) -> Unit) {
        val randomKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val path = File(ctx.getDatabasePath("growurdhan-v9.sqlcipher").parentFile,
            "growurdhan-v9.sqlcipher.restore-" + UUID.randomUUID())
        try {
            store.createNewRestoreCandidate(path, randomKey).use(body)
        } finally { randomKey.fill(0) }
    }
    private fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM " + table, null).use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO vault_account(id,email,first_name,last_name," +
            "password_salt,password_hash,password_iterations,password_changed_at," +
            "q1_id,q1_hash,q2_id,q2_hash,created_at) VALUES " +
            "('native','native@example.invalid','Synthetic','Owner','salt','hash'," +
            "210000,'2026-10-21','city','qhash1','pet','qhash2','2026-10-21')")
        db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,created_at)" +
            " VALUES('owner','INDIVIDUAL','Synthetic Owner','2026-10-21')")
        db.execSQL("INSERT INTO account(account_id,entity_id,account_type,custodian,created_at)" +
            " VALUES('acc','owner','DEMAT','Synthetic Broker','2026-10-21')")
        db.execSQL("INSERT INTO instrument(instrument_id,name,category,created_at)" +
            " VALUES('asset','Synthetic Asset','EQUITY','2026-10-21')")
        db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id,account_id," +
            "current_price_minor,created_at) VALUES('holding','asset','owner','acc'," +
            "9007199254740993,'2026-10-21')")
        db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename," +
            "document_kind,committed_at) VALUES('batch','synthetic.pdf','CAS','2026-10-21')")
        db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
            "holding_name,account_id,event_type,trade_date,quantity_scaled," +
            "unit_price_minor,total_amount_minor,charges_minor,entered_at)" +
            " VALUES('event','owner','holding','Synthetic Asset','acc'," +
            "'BUY','2026-10-20',100000000,9007199254740993,9007199254740993,0,'2026-10-21')")
        db.execSQL("INSERT INTO price_observation(instrument_id,price_date,basis," +
            "price_minor,source,ingested_at) VALUES('asset','2026-10-21'," +
            "'EOD',9007199254740993,'SYNTHETIC','2026-10-21')")
        db.execSQL("INSERT INTO source_document(source_document_id,import_batch_id," +
            "original_filename,document_kind,sha256,byte_length,imported_at) VALUES" +
            "('source','batch','synthetic.pdf','CAS','syntheticsha256',16,'2026-10-21')")
        db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
            "statement_date,quantity_scaled,unit_price_minor) VALUES" +
            "('batch','holding','2026-10-21',100000000,9007199254740993)")
        db.execSQL("INSERT INTO tax_rule_set(assessment_year,rules_json,published_at," +
            "installed_at) VALUES('2026','{}','2026-10-21','2026-10-21')")
        db.execSQL("INSERT INTO liability(liability_id,entity_id,liability_type,name,created_at)" +
            " VALUES('loan','owner','HOME','Synthetic liability','2026-10-21')")
        db.execSQL("INSERT INTO goal(goal_id,entity_id,name,created_at) VALUES" +
            "('goal','owner','Synthetic goal','2026-10-21')")
        db.execSQL("INSERT INTO setting(key,value) VALUES('synthetic','opaque metadata')")
        db.execSQL("INSERT INTO egress_log(egress_log_id,occurred_at,destination,purpose," +
            "outcome) VALUES('audit','2026-10-21','offline','test','BLOCKED')")
        db.execSQL("INSERT INTO listed_security(isin,name,category,price_date,source," +
            "updated_at) VALUES('SYNTHETIC','Synthetic Security','EQUITY'," +
            "'2026-10-21','MANUAL','2026-10-21')")
        db.execSQL("INSERT INTO gmail_seen(message_id,attachment_id,seen_at)" +
            " VALUES('synthetic-message','synthetic-attachment','2026-10-21')")
    }

    @Test fun allNativeV9TableTypesRestoreIntoIndependentEmptySqlcipher() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val original = store.createFresh(false).use { db ->
            db.beginTransaction()
            try { seed(db); db.setTransactionSuccessful() }
            finally { db.endTransaction() }
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(original, email, pass)
        candidate(ctx, store) { target ->
            val inserted = NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                target, archive, email, pass, true)
            assertEquals(original.tables.sumOf { it.rows.size }, inserted)
            assertEquals(original, NativeV9SnapshotReader.capture(target))
            assertEquals(1L, count(target, "liability"))
            assertEquals(1L, count(target, "statement_position"))
        }
    }

    @Test fun wrongPasswordTamperedCipherAndDuplicateRowsNeverPartiallyImport() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val snapshot = store.createFresh(false).use { db ->
            seed(db)
            NativeV9SnapshotReader.capture(db)
        }
        val valid = NativeV9Archive.encrypt(snapshot, email, pass)
        val bad = valid.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        val table = snapshot.tables.single { it.name == "vault_account" }
        val duplicate = snapshot.copy(tables = snapshot.tables.map {
            if (it.name == table.name) it.copy(rows = it.rows + it.rows.first())
            else it
        })
        val poisoned = NativeV9Archive.encrypt(duplicate, email, pass)
        candidate(ctx, store) { target ->
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, valid, email, "Wrong synthetic backup password!".toCharArray(), true)
            }
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, bad, email, pass, true)
            }
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, poisoned, email, pass, true)
            }
            assertEquals(0L, count(target, "entity"))
            assertEquals(0L, count(target, "vault_account"))
            assertEquals(1L, count(target, "vault_meta"))
        }
    }

    @Test fun opaqueHistoricalSchemaExtensionCannotBeSilentlyDroppedOnRestore() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val snapshot = store.createFresh(false).use { db ->
            db.execSQL("ALTER TABLE setting ADD COLUMN opaque_extra TEXT")
            db.execSQL("INSERT INTO setting(key,value,opaque_extra)" +
                " VALUES('historical','value','preserve')")
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(snapshot, email, pass)
        assertEquals(snapshot, NativeV9Archive.decrypt(archive, email, pass))
        candidate(ctx, store) { target ->
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, archive, email, pass, true)
            }
            assertEquals(0L, count(target, "setting"))
            assertEquals(1L, count(target, "vault_meta"))
        }
    }
}
