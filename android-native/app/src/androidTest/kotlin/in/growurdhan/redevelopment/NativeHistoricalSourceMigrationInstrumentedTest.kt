package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic test-only encrypted physical source-v1 through v8 upgrade fixtures.
 * V1 SQL is copied exactly from frozen src/db/schema.ts blob
 * 50b896eb1b017b9ec6426e108c9e13a5ae10f002, in p11-frozen-source-v1.sql.
 * These are NOT captured customer vaults and do not accept P11 real title.
 */
@RunWith(AndroidJUnit4::class)
class NativeHistoricalSourceMigrationInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "p11-source-migration-" + UUID.randomUUID())
            .apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/" + name).also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }

    private fun scalar(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use { c ->
            assertTrue(c.moveToFirst())
            val result = c.getLong(0)
            assertFalse(c.moveToNext())
            result
        }

    private fun select(db: SQLiteDatabase, sql: String): String =
        db.rawQuery(sql, null).use { c ->
            assertTrue(c.moveToFirst())
            val result = c.getString(0)
            assertFalse(c.moveToNext())
            result
        }

    private fun foreignKeysValid(db: SQLiteDatabase) {
        assertEquals(1L, scalar(db, "PRAGMA foreign_keys"))
        db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        assertEquals("ok", select(db, "PRAGMA quick_check"))
    }

    private fun script(text: String): List<Pair<Int?, String>> =
        text.split("-- M24_END_STATEMENT").mapNotNull { part ->
            val ver = Regex("""-- M24_VERSION_(\d+)""").find(part)
                ?.groupValues?.get(1)?.toInt()
            val sql = part.lineSequence()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n").trim()
            if (sql.isBlank()) null else ver to sql
        }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info('" + table + "')", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1) == column) found = true
            found
        }

    private fun fixture(version: Int): Pair<Context, EncryptedVaultStore> {
        require(version in 1..8)
        val context = sandbox()
        val store = EncryptedVaultStore(context)
        store.createFresh(false).use { db ->
            // Disposable synthetic database only: replace empty fresh v9 tables
            // with the *frozen v1 CREATE statements*, not with a v9 version tag.
            db.execSQL("PRAGMA foreign_keys=OFF")
            val tables = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            for (table in tables) {
                require(Regex("[a-z_]+").matches(table))
                db.execSQL("DROP TABLE \"" + table + "\"")
            }
            val v1Source = InstrumentationRegistry.getInstrumentation()
                .context.assets.open("p11-frozen-source-v1.sql")
                .bufferedReader().use { it.readText() }
            val v1 = script(v1Source)
            assertEquals(29, v1.size)
            assertEquals(15, v1.count { it.second.startsWith("CREATE TABLE") })
            for ((_, sql) in v1) {
                if (sql.startsWith("PRAGMA foreign_keys")) continue
                db.execSQL(sql)
            }
            if (version >= 3) {
                // Apply only the frozen source-version DDL through the requested stage;
                // v2 intentionally has no independent upgrade statements.
                val upgrades = context.assets.open("m24-frozen-upgrades.sql")
                    .bufferedReader().use { it.readText() }
                for ((stage, sql) in script(upgrades)) {
                    if (stage == null || stage > version) continue
                    if (sql.startsWith("ALTER TABLE", ignoreCase = true)) {
                        val parts = Regex(
                            """(?i)^ALTER TABLE ([a-z_]+) ADD COLUMN ([a-z_]+)\b""")
                            .find(sql) ?: error("Unexpected frozen ALTER statement")
                        if (hasColumn(db, parts.groupValues[1], parts.groupValues[2])) continue
                    }
                    db.execSQL(sql)
                }
            }
            db.execSQL("INSERT INTO vault_meta(key,value,updated_at) " +
                "VALUES('schema_version',?,?)",
                arrayOf<Any>(version.toString(), "2026-10-28"))
            db.execSQL("PRAGMA user_version=" + version)
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name," +
                "notes,created_at) VALUES('historical-owner','INDIVIDUAL'," +
                "'Test-only historic owner','unaltered owner note','2025-01-01')")
            db.execSQL("INSERT INTO account(account_id,entity_id,account_type," +
                "custodian,nominee,created_at) VALUES('historical-custody'," +
                "'historical-owner','OTHER','Historical test registrar'," +
                "'Test-only nominee','2025-01-01')")
            db.execSQL("INSERT INTO instrument(instrument_id,name,category," +
                "sub_category,created_at) VALUES('historical-instrument'," +
                "'Historical direct property','REAL_ESTATE','Residential','2025-01-01')")
            db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id," +
                "account_id,current_price_minor,notes,created_at) VALUES(" +
                "'historical-holding','historical-instrument','historical-owner'," +
                "'historical-custody',876543,'historical holding','2025-01-01')")
            db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
                "holding_name,event_type,trade_date,quantity_scaled,unit_price_minor," +
                "total_amount_minor,charges_minor,notes,entered_at) VALUES(" +
                "'historical-ledger','historical-owner','historical-holding'," +
                "'Historical direct property','OPENING_BALANCE','2025-01-01'," +
                "1234567890,100,1234500,125,'exact immutable event','2025-01-01')")
            db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename," +
                "document_kind,committed_at) VALUES('historical-batch'," +
                "'synthetic-source.csv','CSV','2025-01-01')")
            db.execSQL("INSERT INTO source_document(source_document_id,import_batch_id," +
                "original_filename,document_kind,sha256,byte_length,imported_at) " +
                "VALUES('historical-document','historical-batch','test-only.csv'," +
                "'CSV','synthetic-sha-opaque',23,'2025-01-01')")
            db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
                "statement_date,quantity_scaled,unit_price_minor) VALUES(" +
                "'historical-batch','historical-holding','2025-01-01',1234567890,100)")
            db.execSQL("INSERT INTO goal(goal_id,entity_id,name,created_at) " +
                "VALUES('historical-goal','historical-owner','Test-only goal','2025-01-01')")
            db.execSQL("ALTER TABLE setting ADD COLUMN historical_opaque BLOB")
            db.execSQL("INSERT INTO setting(key,value,historical_opaque) VALUES(" +
                "'historical-state','exact text',X'0001FF007F')")
            foreignKeysValid(db)
            assertEquals(version.toLong(), scalar(db, "PRAGMA user_version"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type='table' AND name='native_p11_property_proof'"))
            if (version == 1) {
                assertEquals(15L, scalar(db, "SELECT COUNT(*) FROM sqlite_master " +
                    "WHERE type='table' AND name NOT LIKE 'sqlite_%'"))
            }
        }
        return context to store
    }

    private fun preserved(db: SQLiteDatabase) {
        foreignKeysValid(db)
        assertEquals("unaltered owner note", select(db,
            "SELECT notes FROM entity WHERE entity_id='historical-owner'"))
        assertEquals("Test-only nominee", select(db,
            "SELECT nominee FROM account WHERE account_id='historical-custody'"))
        assertEquals(876543L, scalar(db,
            "SELECT current_price_minor FROM holding WHERE holding_id='historical-holding'"))
        assertEquals(1234567890L, scalar(db,
            "SELECT quantity_scaled FROM ledger_event WHERE ledger_event_id='historical-ledger'"))
        assertEquals(1234500L, scalar(db,
            "SELECT total_amount_minor FROM ledger_event WHERE ledger_event_id='historical-ledger'"))
        assertEquals(125L, scalar(db,
            "SELECT charges_minor FROM ledger_event WHERE ledger_event_id='historical-ledger'"))
        assertEquals("exact immutable event", select(db,
            "SELECT notes FROM ledger_event WHERE ledger_event_id='historical-ledger'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM statement_position WHERE import_batch_id='historical-batch'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM source_document WHERE source_document_id='historical-document'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM goal WHERE goal_id='historical-goal'"))
        assertEquals("0001FF007F", select(db,
            "SELECT hex(historical_opaque) FROM setting WHERE key='historical-state'"))
        assertEquals(1L, scalar(db,
            "SELECT typeof(historical_opaque)='blob' FROM setting " +
            "WHERE key='historical-state'"))
        assertEquals(0L, scalar(db,
            "SELECT COUNT(*) FROM sqlite_master " +
            "WHERE type='table' AND name='native_p11_property_proof'"))
    }

    @Test fun frozenSourceV1EncryptedUpgradePreservesBooksLedgerAndOpaqueCells() {
        val (_, store) = fixture(1)
        store.openExisting().use { db ->
            assertEquals(9L, scalar(db, "PRAGMA user_version"))
            assertEquals(18L, scalar(db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%'"))
            preserved(db)
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM ledger_event WHERE ledger_event_id='historical-ledger'")
            }
        }
        store.openExisting().use { preserved(it) }
    }

    @Test fun frozenSourceV8EncryptedUpgradePreservesBooksLedgerAndOpaqueCells() {
        val (_, store) = fixture(8)
        store.openExisting().use { db ->
            assertEquals(9L, scalar(db, "PRAGMA user_version"))
            preserved(db)
        }
        store.openExisting().use { preserved(it) }
    }

    @Test fun frozenSourceV2ThroughV7EncryptedUpgradesRetainTypedFinancialHistory() {
        // Six distinct source-version physical layouts, each populated BEFORE
        // the production upgrader runs; unlike an intact v9 database with
        // only a changed user_version marker.
        for (version in 2..7) {
            val (_, store) = fixture(version)
            store.openExisting().use { db ->
                assertEquals("Failed source stage $version", 9L,
                    scalar(db, "PRAGMA user_version"))
                assertEquals("9", select(db,
                    "SELECT value FROM vault_meta WHERE key='schema_version'"))
                assertEquals(18L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                        "AND name NOT LIKE 'sqlite_%'"))
                preserved(db)
                assertThrows(Exception::class.java) {
                    db.execSQL("UPDATE ledger_event SET notes='changed' " +
                        "WHERE ledger_event_id='historical-ledger'")
                }
            }
            store.openExisting().use { db ->
                assertEquals(9L, scalar(db, "PRAGMA user_version"))
                preserved(db)
            }
        }
    }

    @Test fun frozenSourceV5OrphanCustodyFailsBeforeAnyUpgradeOrRepair() {
        val (ctx, store) = fixture(5)
        val key = KeystoreWrappedVaultKey(ctx).unwrapExisting()
        try {
            val file = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
            SQLiteDatabase.openDatabase(file.absolutePath, key, null,
                SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { _, _ ->
                    throw IllegalStateException("Preserve synthetic source on corruption")
                }, null).use { db ->
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("INSERT INTO account(account_id,entity_id,account_type," +
                    "custodian,created_at) VALUES('orphan-custody'," +
                    "'missing-owner','OTHER','Test custodian','2025-01-01')")
            }
        } finally { key.fill(0) }
        assertThrows(Exception::class.java) { store.openExisting().close() }
        val afterKey = KeystoreWrappedVaultKey(ctx).unwrapExisting()
        try {
            SQLiteDatabase.openDatabase(
                ctx.getDatabasePath("growurdhan-v9.sqlcipher").absolutePath,
                afterKey, null, SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { _, _ ->
                    throw IllegalStateException("Preserve rejected source")
                }, null).use { db ->
                db.execSQL("PRAGMA foreign_keys=ON")
                assertEquals(5L, scalar(db, "PRAGMA user_version"))
                assertEquals("5", select(db,
                    "SELECT value FROM vault_meta WHERE key='schema_version'"))
                assertEquals(16L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                        "AND name NOT LIKE 'sqlite_%'"))
                assertEquals(0L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                        "AND name IN ('vault_account','gmail_seen','native_p11_property_proof')"))
                assertEquals(1L, scalar(db,
                    "SELECT COUNT(*) FROM account WHERE account_id='orphan-custody'"))
                assertEquals(1L, scalar(db,
                    "SELECT COUNT(*) FROM entity WHERE entity_id='historical-owner'"))
                assertEquals(876543L, scalar(db,
                    "SELECT current_price_minor FROM holding " +
                        "WHERE holding_id='historical-holding'"))
                assertEquals(1234500L, scalar(db,
                    "SELECT total_amount_minor FROM ledger_event " +
                        "WHERE ledger_event_id='historical-ledger'"))
                assertEquals("0001FF007F", select(db,
                    "SELECT hex(historical_opaque) FROM setting " +
                        "WHERE key='historical-state'"))
                db.rawQuery("PRAGMA foreign_key_check", null).use { c ->
                    assertTrue("Original orphan must not be silently repaired",
                        c.moveToFirst())
                }
                assertEquals("ok", select(db, "PRAGMA quick_check"))
            }
        } finally { afterKey.fill(0) }
    }

    @Test fun incompatibleFrozenV1UpgradeRollsBackAllNewTablesAndVersion() {
        val (badContext, badStore) = fixture(1)
        val key = KeystoreWrappedVaultKey(badContext).unwrapExisting()
        try {
            val file = badContext.getDatabasePath("growurdhan-v9.sqlcipher")
            val preserve = DatabaseErrorHandler { _, _ ->
                throw IllegalStateException("Keep failed historical source intact")
            }
            SQLiteDatabase.openDatabase(file.absolutePath, key, null,
                SQLiteDatabase.OPEN_READWRITE, preserve, null).use { db ->
                db.execSQL("ALTER TABLE entity ADD COLUMN cas_id_source " +
                    "INTEGER NOT NULL DEFAULT 0")
            }
        } finally { key.fill(0) }
        assertThrows(Exception::class.java) { badStore.openExisting().close() }
        val evidenceKey = KeystoreWrappedVaultKey(badContext).unwrapExisting()
        try {
            val preserve = DatabaseErrorHandler { _, _ ->
                throw IllegalStateException("Preserve source")
            }
            SQLiteDatabase.openDatabase(
                badContext.getDatabasePath("growurdhan-v9.sqlcipher").absolutePath,
                evidenceKey, null, SQLiteDatabase.OPEN_READWRITE, preserve, null).use { db ->
                db.execSQL("PRAGMA foreign_keys=ON")
                assertEquals(1L, scalar(db, "PRAGMA user_version"))
                assertEquals("1", select(db,
                    "SELECT value FROM vault_meta WHERE key='schema_version'"))
                assertEquals(0L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('listed_security','vault_account','gmail_seen')"))
                preserved(db)
                assertEquals(15L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%'"))
                assertEquals("integer", select(db,
                    "SELECT type FROM pragma_table_info('entity') " +
                    "WHERE name='cas_id_source'").lowercase())
            }
        } finally { evidenceKey.fill(0) }
    }
}
