package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.RestoreV9InsertTransaction
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

    private val preserve = DatabaseErrorHandler { _, _ ->
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
    @Test fun syntheticV8MarkerAdvancesAtomicallyWithoutRecreatingVault() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        store.createFresh(false).use { db ->
            db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,created_at) " +
                "VALUES ('synthetic-old','INDIVIDUAL','Owned','2026-10-16')")
            db.execSQL("PRAGMA user_version=8")
            db.execSQL("UPDATE vault_meta SET value='8' WHERE key='schema_version'")
        }
        store.openExisting().use { db ->
            assertEquals(9L, scalar(db, "PRAGMA user_version"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM entity WHERE entity_id='synthetic-old'"))
        }
    }

    @Test fun missingMandatoryTableRejectsMigrationWithoutChangingRecordsOrVersion() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        store.createFresh(false).use { db ->
            db.execSQL("PRAGMA user_version=8")
            db.execSQL("UPDATE vault_meta SET value='8' WHERE key='schema_version'")
            db.execSQL("DROP TABLE instrument")
        }
        assertThrows(Exception::class.java) { store.openExisting().close() }
        val key = KeystoreWrappedVaultKey(ctx).unwrapExisting()
        try {
            SQLiteDatabase.openDatabase(
                ctx.getDatabasePath("growurdhan-v9.sqlcipher").absolutePath,
                key, null, SQLiteDatabase.OPEN_READWRITE, preserve, null).use { db ->
                assertEquals(8L, scalar(db, "PRAGMA user_version"))
                assertEquals(0L, scalar(db,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='instrument'"))
            }
        } finally { key.fill(0) }
    }


    @Test fun stagedCiphertextNeverOverwritesExistingPendingRestore() {
        val ctx = sandbox()
        val staging = LockedRestoreStaging(ctx)
        // Plain or unknown envelopes must be refused without a file write.
        assertThrows(IllegalArgumentException::class.java) {
            staging.stageEncryptedArchiveOnce(
                """{"holdings":[{"ownerId":"synthetic"}]}""".toByteArray()
            )
        }
        val pending = File(ctx.noBackupFilesDir, "restore-pending.v1.kosha")
        assertFalse(pending.exists())

        // Staging stores untrusted ciphertext for later AUTHENTICATED review.
        // It must not approve even structurally correct but unauthenticated GCM.
        val corrupted = ByteArray(56)
        "GUDVLT01".toByteArray(Charsets.US_ASCII).copyInto(corrupted)
        val receipt = staging.stageEncryptedArchiveOnce(corrupted)
        assertEquals(56, receipt.byteCount)
        // No silent blank SQLCipher vault while a restore review is pending.
        assertThrows(IllegalStateException::class.java) {
            EncryptedVaultStore(ctx).createFresh(false).close()
        }
        val preserved = pending.readBytes()
        assertThrows(IllegalStateException::class.java) {
            staging.stageEncryptedArchiveOnce(corrupted)
        }
        assertThrows(Exception::class.java) { staging.inspect("m24@example.invalid", "wrong") }
        assertArrayEquals(preserved, pending.readBytes())
        assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }


    @Test fun strictAuthenticatedJsonV9PreviewDoesNotInventMoneyEvents() {
        // Synthetic full record, NEVER a genuine user archive. Amount larger
        // than JS safe integer must survive as a Kotlin Long, without rounding.
        val exact = """{
          "kind":"GUDVAULT","version":2,"exportedAt":"2026-10-16T00:00:00Z",
          "account":{"email":"owner@example.invalid","firstName":"Fixture","lastName":"Only",
            "passwordSalt":"syntheticSalt","passwordHash":"syntheticHash",
            "passwordIterations":210000,"passwordChangedAt":"2026-10-16",
            "q1Id":"city","q1Hash":"syntheticQ1","q2Id":"pet","q2Hash":"syntheticQ2",
            "createdAt":"2026-10-16","panMasked":"ABCDE****F",
            "panForStatements":"ABCDE1234F","dateOfBirth":"1990-01-01"},
          "familyMembers":[{"id":"owner-A","name":"Fixture","relation":"Self","color":"#112233"}],
          "accounts":[{"id":"account-A","ownerId":"owner-A","accountType":"DEMAT","custodian":"Test",
            "dpId":"DP000123","clientId":"CL987","boId":"BO-SYNTH","nominee":"Fixture Only"}],
          "holdings":[{"id":"holding-A","ownerId":"owner-A","accountId":"account-A",
            "name":"Example","category":"EQUITY","subCategory":"Large cap",
            "isin":"INE000TEST00","needsReview":true,"notes":"synthetic holding note",
            "symbolOrFolio":"TEST","quantityScaled":"1234567890123",
            "currentPriceMinor":"9007199254740993","lastPriceUpdated":"2026-10-16",
            "priceSource":"Test only"}],
          "ledgerEvents":[{"id":"event-A","ownerId":"owner-A","holdingId":"holding-A",
            "holdingName":"Example","eventType":"BUY","eventDate":"2026-10-16",
            "quantityScaled":"1234567890123","unitPriceMinor":"3",
            "totalAmountMinor":"9007199254740993","createdAt":"2026-10-16",
            "importBatchId":"test-batch","sourceRef":"synthetic-import-ref",
            "notes":"original event note"}],
          "goals":[{"id":"goal-A","ownerId":"owner-A","name":"Test goal",
            "targetMinor":"100000","createdAt":"2026-10-16",
            "priority":"HIGH"}],
          "unknownOpaque":{"nested":[true,"untouched"]}
        }""".trimIndent()
        val plan = FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(exact)
        assertEquals(8, plan.rows.size)
        assertEquals(exact, plan.rows.last().values.last())
        assertTrue(plan.rows.single { it.table == "vault_account" }.values
            .contains("ABCDE1234F"))
        assertTrue(plan.rows.single { it.table == "ledger_event" }.values
            .contains(9007199254740993L))
        fun mapped(table: String, col: String): Any? {
            val row = plan.rows.single { it.table == table }
            return row.values[row.columns.indexOf(col).also { require(it >= 0) }]
        }
        assertEquals("DP000123", mapped("account", "dp_id"))
        assertEquals("CL987", mapped("account", "client_id"))
        assertEquals("BO-SYNTH", mapped("account", "bo_id"))
        assertEquals("Fixture Only", mapped("account", "nominee"))
        assertEquals("INE000TEST00", mapped("instrument", "isin"))
        assertEquals(1L, mapped("holding", "needs_review"))
        assertEquals("synthetic holding note", mapped("holding", "notes"))
        assertEquals("test-batch", mapped("ledger_event", "import_batch_id"))
        assertEquals("synthetic-import-ref", mapped("ledger_event", "source_ref"))
        assertEquals("original event note", mapped("ledger_event", "notes"))
        assertEquals("HIGH", mapped("goal", "priority"))

        // Real SQLCipher data-path proof, not just a mocked transaction.
        // These rows are synthetic and only ever touch a private isolated vault.
        val successContext = sandbox()
        val successStore = EncryptedVaultStore(successContext)
        successStore.createFresh(false).use { db ->
            RestoreV9InsertTransaction.apply(plan, AndroidRestoreV9SqlTarget(db))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM entity"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
            assertEquals(9007199254740993L,
                scalar(db, "SELECT total_amount_minor FROM ledger_event"))
            db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(
                "m24.authenticated_frozen_original_json")).use { result ->
                assertTrue(result.moveToFirst())
                assertEquals(exact, result.getString(0))
            }
            assertThrows(IllegalArgumentException::class.java) {
                RestoreV9InsertTransaction.apply(plan, AndroidRestoreV9SqlTarget(db))
            }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
        }
        successStore.openExisting().use { db ->
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
            assertEquals(9007199254740993L,
                scalar(db, "SELECT total_amount_minor FROM ledger_event"))
        }

        // Introduce a foreign-key violation into a separate synthetic plan
        // without changing its authenticated source digest. The target must
        // roll back ALL previous inserts: entity and vault_account included.
        val account = plan.rows.single { it.table == "account" }
        val invalidAccount = account.copy(values = account.values.mapIndexed { index, value ->
            if (account.columns[index] == "entity_id") "missing-owner" else value
        })
        val invalidPlan = plan.copy(rows = plan.rows.map {
            if (it.table == "account") invalidAccount else it
        })
        val rejectedContext = sandbox()
        val rejectedStore = EncryptedVaultStore(rejectedContext)
        rejectedStore.createFresh(false).use { db ->
            assertThrows(Exception::class.java) {
                RestoreV9InsertTransaction.apply(
                    invalidPlan, AndroidRestoreV9SqlTarget(db))
            }
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM entity"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM vault_account"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
        }
        rejectedStore.openExisting().use { db ->
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM entity"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
        }


        assertThrows(Exception::class.java) {
            FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(exact.replace(
                "\"totalAmountMinor\":\"9007199254740993\"",
                "\"totalAmountMinor\":9007199254740993"))
        }
        assertThrows(Exception::class.java) {
            FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(exact.replace(
                "\"eventType\":\"BUY\"", "\"eventType\":\"FAKE\""))
        }
        assertThrows(Exception::class.java) {
            FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(exact.replace(
                "\"name\":\"Example\"", "\"name\":\"\""))
        }
        assertThrows(Exception::class.java) {
            FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(exact.replace(
                "\"kind\":\"GUDVAULT\"", "\"kind\":\"UNKNOWN\""))
        }
    }

}
