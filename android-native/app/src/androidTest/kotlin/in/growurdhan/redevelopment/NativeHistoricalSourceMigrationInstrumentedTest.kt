package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativePropertySignedEvidence
import `in`.growurdhan.model.NativeRegistrationProof
import `in`.growurdhan.model.NativeSharedPropertyAggregation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.time.LocalDate
import org.bouncycastle.jce.provider.BouncyCastleProvider
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


    private val testProvider = BouncyCastleProvider()
    private val syntheticRegistrar = KeyPairGenerator
        .getInstance("Ed25519", testProvider).generateKeyPair()
    private val syntheticAppraiser = KeyPairGenerator
        .getInstance("Ed25519", testProvider).generateKeyPair()
    private val syntheticRoots = NativePropertySignedEvidence.TrustRoots(
        mapOf("test-only-registrar" to syntheticRegistrar.public.encoded),
        mapOf("test-only-appraiser" to syntheticAppraiser.public.encoded))
    private val proofDate = LocalDate.parse("2026-10-27")
    private val testEnrollment = NativeCredentials.Enrollment(
        "migration-test@example.org", "Migration", "Fixture", "Secure!Pass123",
        "school", "Old School", "pet", "Old Pet")

    /** Only test scaffolding: the frozen v1-v5 never carried native credentials.
     * Auth is provisioned AFTER the source-derived migration, without enrolling
     * a fresh database or overwriting its historical owners and financial rows.
     */
    private fun admittedAfterMigration(ctx: Context, store: EncryptedVaultStore):
        NativeAccountRepository {
        val credentials = NativeCredentials.enroll(
            testEnrollment, Instant.now().toString())
        store.openExisting().use { db ->
            preserved(db)
            db.beginTransaction()
            try {
                db.execSQL("INSERT INTO vault_account(id,email,first_name,last_name," +
                    "password_salt,password_hash,password_iterations," +
                    "password_changed_at,q1_id,q1_hash,q2_id,q2_hash,created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>("native-account", credentials.email,
                        credentials.firstName, credentials.lastName,
                        credentials.passwordSalt, credentials.passwordHash,
                        credentials.passwordIterations, credentials.passwordChangedAt,
                        credentials.q1Id, credentials.q1Hash, credentials.q2Id,
                        credentials.q2Hash, credentials.createdAt))
                for ((key, value) in listOf(
                    "m25.terms.version" to NativeRegistrationProof.TERMS_VERSION,
                    "m25.terms.text_id" to NativeRegistrationProof.TERMS_TEXT_ID,
                    "m25.terms.accepted_at" to "2026-10-19T10:00:00Z")) {
                    db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                        arrayOf(key, value))
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        return loginExisting(ctx)
    }

    private fun loginExisting(ctx: Context): NativeAccountRepository {
        val gate = NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }
        val accounts = NativeAccountRepository(ctx, gate)
        assertTrue("Test-only migrated vault credentials should authenticate",
            accounts.login(testEnrollment.email, testEnrollment.password))
        return accounts
    }

    private fun sign(key: KeyPair, message: ByteArray): ByteArray =
        Signature.getInstance("Ed25519", testProvider).run {
            initSign(key.private)
            update(message)
            sign()
        }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun signedProof(
        owner: String = "historical-owner",
        custody: String = "historical-custody",
        grossMinor: Long = 100_000L
    ): NativePropertySignedEvidence.Proof {
        val registryBytes = "disposable test registrar source row".toByteArray()
        val appraisalBytes = "disposable independent test appraisal row".toByteArray()
        val claim = NativeSharedPropertyAggregation.Claim(
            "historical-holding", owner, "historical-test-property-id",
            "test-only-registered-source", digest(registryBytes),
            LocalDate.parse("2026-09-01"), "60", grossMinor,
            "test-only-independent-appraisal", LocalDate.parse("2026-10-01"))
        val unsigned = NativePropertySignedEvidence.Proof(
            claim, custody, "test-only-registrar", "test-only-appraiser",
            registryBytes, ByteArray(0), appraisalBytes, digest(appraisalBytes),
            ByteArray(0))
        return unsigned.copy(
            registrySignature = sign(syntheticRegistrar,
                NativePropertySignedEvidence.registryMessage(unsigned)),
            valuationSignature = sign(syntheticAppraiser,
                NativePropertySignedEvidence.appraisalMessage(unsigned)))
    }

    private fun installedProofCount(db: SQLiteDatabase): Long =
        scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
            "AND name='native_p11_property_proof'")

    private fun preservedAfterProperty(db: SQLiteDatabase) {
        foreignKeysValid(db)
        assertEquals(9L, scalar(db, "PRAGMA user_version"))
        assertEquals("9", select(db,
            "SELECT value FROM vault_meta WHERE key='schema_version'"))
        assertEquals("unaltered owner note", select(db,
            "SELECT notes FROM entity WHERE entity_id='historical-owner'"))
        assertEquals("Test-only nominee", select(db,
            "SELECT nominee FROM account WHERE account_id='historical-custody'"))
        assertEquals(876543L, scalar(db,
            "SELECT current_price_minor FROM holding " +
                "WHERE holding_id='historical-holding'"))
        assertEquals(1234567890L, scalar(db,
            "SELECT quantity_scaled FROM ledger_event " +
                "WHERE ledger_event_id='historical-ledger'"))
        assertEquals(1234500L, scalar(db,
            "SELECT total_amount_minor FROM ledger_event " +
                "WHERE ledger_event_id='historical-ledger'"))
        assertEquals(125L, scalar(db,
            "SELECT charges_minor FROM ledger_event " +
                "WHERE ledger_event_id='historical-ledger'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM source_document " +
                "WHERE source_document_id='historical-document'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM statement_position " +
                "WHERE import_batch_id='historical-batch'"))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM goal WHERE goal_id='historical-goal'"))
        assertEquals("0001FF007F", select(db,
            "SELECT hex(historical_opaque) FROM setting " +
                "WHERE key='historical-state'"))
        assertThrows(Exception::class.java) {
            db.execSQL("UPDATE ledger_event SET notes='forged' " +
                "WHERE ledger_event_id='historical-ledger'")
        }
        assertEquals(1L, installedProofCount(db))
        assertEquals(1L, scalar(db,
            "SELECT COUNT(*) FROM native_p11_property_proof " +
                "WHERE holding_id='historical-holding' AND " +
                "entity_id='historical-owner' AND " +
                "account_id='historical-custody'"))
        assertEquals(100_000L, scalar(db,
            "SELECT gross_value_minor FROM native_p11_property_proof"))
        assertEquals(19L, scalar(db,
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type='table' AND name NOT LIKE 'sqlite_%'"))
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


    @Test fun sourceV1ThroughV8FirstSignedProofInstallIsAtomicAndPreservesLedger() {
        // Each iteration is a distinct encrypted version-specific physical DB;
        // no older fixture is just a v9 layout with a relabelled version marker.
        for (version in 1..8) {
            val (ctx, store) = fixture(version)
            val accounts = admittedAfterMigration(ctx, store)
            val repo = NativePropertyProofRepository(accounts, syntheticRoots)
            assertNull(repo.preview(testEnrollment.email, proofDate))
            val source = signedProof()
            assertThrows(IllegalArgumentException::class.java) {
                NativePropertyProofRepository(accounts)
                    .register(testEnrollment.email, source, proofDate)
            }
            accounts.withUnlockedVault(testEnrollment.email) { db ->
                preserved(db)
                assertEquals(0L, installedProofCount(db))
            }
            repo.register(testEnrollment.email, source, proofDate)
            val comparison = repo.preview(testEnrollment.email, proofDate)!!
            assertEquals(100_000L, comparison.householdGrossMinor)
            assertEquals(60_000L, comparison.attributedMinor)
            assertEquals(40_000L, comparison.unallocatedMinor)
            accounts.withUnlockedVault(testEnrollment.email) { db ->
                preservedAfterProperty(db)
                assertEquals("disposable test registrar source row",
                    select(db, "SELECT CAST(registry_bytes AS TEXT) " +
                        "FROM native_p11_property_proof"))
                assertThrows(Exception::class.java) {
                    db.execSQL("DELETE FROM native_p11_property_proof")
                }
            }
            accounts.onBackground()
            val restarted = loginExisting(ctx)
            val reopenedRepo = NativePropertyProofRepository(restarted, syntheticRoots)
            assertEquals("Source version $version mismatch after encrypted restart",
                comparison, reopenedRepo.preview(testEnrollment.email, proofDate))
            restarted.withUnlockedVault(testEnrollment.email) { db ->
                preservedAfterProperty(db)
            }
            assertThrows(IllegalArgumentException::class.java) {
                NativePropertyProofRepository(restarted)
                    .preview(testEnrollment.email, proofDate)
            }
        }
    }

    @Test fun migratedV5RejectsSignedForeignOwnerCustodyAndWrongDigestWithoutTable() {
        val (ctx, store) = fixture(5)
        val accounts = admittedAfterMigration(ctx, store)
        val repo = NativePropertyProofRepository(accounts, syntheticRoots)
        val original = signedProof()
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(testEnrollment.email,
                signedProof(owner = "not-the-original-owner"), proofDate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(testEnrollment.email,
                signedProof(custody = "not-original-custody"), proofDate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(testEnrollment.email,
                original.copy(registrySourceBytes = "wrong digest".toByteArray()),
                proofDate)
        }
        accounts.withUnlockedVault(testEnrollment.email) { db ->
            preserved(db)
            assertEquals(0L, installedProofCount(db))
        }
        repo.register(testEnrollment.email, original, proofDate)
        val comparison = repo.preview(testEnrollment.email, proofDate)!!
        for (rejected in listOf(original, signedProof(grossMinor = 200_000L))) {
            assertThrows(Exception::class.java) {
                repo.register(testEnrollment.email, rejected, proofDate)
            }
        }
        assertEquals(comparison, repo.preview(testEnrollment.email, proofDate))
        accounts.withUnlockedVault(testEnrollment.email) { db ->
            preservedAfterProperty(db)
        }
    }

    @Test fun migratedV8PreexistingDamagedProofSchemaRejectsWithoutRepair() {
        val (ctx, store) = fixture(8)
        val accounts = admittedAfterMigration(ctx, store)
        accounts.withUnlockedVault(testEnrollment.email) { db ->
            // Malformed partial installed extension: fail closed, never bless
            // it or create missing columns/triggers inside a failed admission.
            db.execSQL("CREATE TABLE native_p11_property_proof(" +
                "holding_id TEXT PRIMARY KEY NOT NULL," +
                "property_id TEXT NOT NULL)")
        }
        val repo = NativePropertyProofRepository(accounts, syntheticRoots)
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(testEnrollment.email, signedProof(), proofDate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.preview(testEnrollment.email, proofDate)
        }
        accounts.withUnlockedVault(testEnrollment.email) { db ->
            assertEquals(1L, installedProofCount(db))
            assertEquals(0L, scalar(db,
                "SELECT COUNT(*) FROM native_p11_property_proof"))
            assertEquals(0L, scalar(db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' " +
                    "AND name LIKE 'native_p11_proof_no_%'"))
            assertEquals(2L, scalar(db,
                "SELECT COUNT(*) FROM pragma_table_info('native_p11_property_proof')"))
            assertEquals(876543L, scalar(db,
                "SELECT current_price_minor FROM holding " +
                    "WHERE holding_id='historical-holding'"))
            assertEquals(1234500L, scalar(db,
                "SELECT total_amount_minor FROM ledger_event " +
                    "WHERE ledger_event_id='historical-ledger'"))
            assertEquals("0001FF007F", select(db,
                "SELECT hex(historical_opaque) FROM setting " +
                    "WHERE key='historical-state'"))
            foreignKeysValid(db)
        }
        accounts.onBackground()
        val reopened = loginExisting(ctx)
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertyProofRepository(reopened, syntheticRoots)
                .preview(testEnrollment.email, proofDate)
        }
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
