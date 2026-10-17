package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.RestoreV9InsertTransaction
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.database.RestoreOnceJournal
import `in`.growurdhan.database.EncryptedRestorePublisher
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
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

    /**
     * Synthetic, non-user cryptographic fixture. Creates the same binary
     * authenticated envelope framing as the frozen GUDVLT01 exporter.
     * No passwords, PAN or vault keys are persisted in CI artifacts.
     */
    private fun newSyntheticEncryptedBackup(
        modern: Boolean, email: String, password: String
    ): Pair<ByteArray, RestoreIdentityGate.Credentials> {
        val random = SecureRandom()
        fun pbkdf(secret: String, salt: ByteArray, rounds: Int): ByteArray {
            val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, 256)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
            } finally { spec.clearPassword() }
        }
        fun saltedHash(secret: String): Pair<String, String> {
            val salt = ByteArray(16).also(random::nextBytes)
            return Base64.getEncoder().encodeToString(salt) to
                Base64.getEncoder().encodeToString(pbkdf(secret, salt, 210_000))
        }
        val (passwordSalt, passwordHash) = saltedHash(password)
        fun answerPack(answer: String): String {
            val (salt, hash) = saltedHash(answer)
            return salt + ":" + hash + ":210000"
        }
        val q1 = answerPack("blue")
        val q2 = answerPack("river")
        val account = JSONObject()
            .put("email", email).put("firstName", "Synthetic")
            .put("lastName", "Fixture").put("passwordSalt", passwordSalt)
            .put("passwordHash", passwordHash).put("passwordIterations", 210_000)
            .put("passwordChangedAt", "2026-10-17")
            .put("q1Id", "city").put("q1Hash", q1)
            .put("q2Id", "pet").put("q2Hash", q2)
            .put("createdAt", "2026-10-17").put("dateOfBirth", "1990-01-01")
            .put("panForStatements", "ABCDE1234F").put("panMasked", "ABCDE****F")
        val original = JSONObject()
            .put("kind", "GUDVAULT").put("version", 2)
            .put("exportedAt", "2026-10-17T00:00:00Z")
            .put("account", account)
            .put("familyMembers", JSONArray().put(
                JSONObject().put("id", "owner")
                    .put("name", "Synthetic").put("relation", "Self")))
            .put("accounts", JSONArray().put(
                JSONObject().put("id", "account").put("ownerId", "owner")
                    .put("accountType", "DEMAT").put("custodian", "Synthetic")))
            .put("holdings", JSONArray().put(
                JSONObject().put("id", "holding").put("ownerId", "owner")
                    .put("accountId", "account").put("name", "Synthetic instrument")
                    .put("category", "EQUITY").put("subCategory", "")
                    .put("symbolOrFolio", "SYNTH")
                    .put("quantityScaled", "1234567890123")
                    .put("currentPriceMinor", "9007199254740993")
                    .put("lastPriceUpdated", "2026-10-17")
                    .put("priceSource", "synthetic-only")))
            .put("ledgerEvents", JSONArray().put(
                JSONObject().put("id", "event").put("ownerId", "owner")
                    .put("holdingId", "holding")
                    .put("holdingName", "Synthetic instrument")
                    .put("eventType", "BUY").put("eventDate", "2026-10-17")
                    .put("quantityScaled", "1234567890123")
                    .put("unitPriceMinor", "100")
                    .put("totalAmountMinor", "9007199254740993")
                    .put("createdAt", "2026-10-17")))
            .put("goals", JSONArray().put(
                JSONObject().put("id", "goal").put("ownerId", "owner")
                    .put("name", "Synthetic goal")
                    .put("targetMinor", "10000")
                    .put("createdAt", "2026-10-17")))
            .put("unknownOpaque", JSONObject().put("untouched", true))
            .toString()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val material = if (modern) {
            "GrowUrDhan\n" + email.lowercase() + "\n" + password
        } else password
        val rawKey = pbkdf(material, salt, 600_000)
        val ciphertext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(rawKey, "AES"),
                GCMParameterSpec(128, nonce))
            cipher.doFinal(original.toByteArray(Charsets.UTF_8))
        } finally { rawKey.fill(0) }
        val envelope = if (modern) {
            ByteBuffer.allocate(40 + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
                .put("GUDVLT01".toByteArray(Charsets.US_ASCII))
                .putInt(600_000).put(salt).put(nonce).put(ciphertext).array()
        } else {
            JSONObject().put("magic", "KOSHA1").put("kdf", "PBKDF2-SHA256")
                .put("iterations", 600_000)
                .put("saltB64", Base64.getEncoder().encodeToString(salt))
                .put("ivB64", Base64.getEncoder().encodeToString(nonce))
                .put("cipherB64", Base64.getEncoder().encodeToString(ciphertext))
                .toString().toByteArray(Charsets.UTF_8)
        }
        val credentials = RestoreIdentityGate.Credentials(
            email, password, "ABCDE1234F", "1990-01-01",
            "city", "blue", "pet", "river")
        return envelope to credentials
    }

    @Test fun orphanedRestoreClaimsAndCandidatesBlockAnyNewVaultWithoutDeletingEvidence() {
        val (archive, credentials) = newSyntheticEncryptedBackup(
            true, "recovery@example.invalid", "synthetic-recovery-password")
        val variants = listOf("claim", "candidate-key", "candidate-db", "candidate-wal")
        for (variant in variants) {
            val ctx = sandbox()
            val staging = LockedRestoreStaging(ctx)
            staging.stageEncryptedArchiveOnce(archive)
            val pending = File(ctx.noBackupFilesDir, "restore-pending.v1.kosha")
            val pendingBytes = pending.readBytes()
            val marker = when (variant) {
                "claim" -> {
                    val claims = File(ctx.noBackupFilesDir, "restore-claims").apply { mkdirs() }
                    RestoreOnceJournal(claims.toPath()).claimOnce("a".repeat(64))
                    File(claims, "restore-" + "a".repeat(64) + ".claim")
                }
                "candidate-key" -> File(ctx.noBackupFilesDir,
                    "vault-key.v1.restore-" + UUID.randomUUID()).apply {
                    writeBytes(byteArrayOf(3, 4, 5, 6))
                }
                "candidate-db" -> File(ctx.getDatabasePath("growurdhan-v9.sqlcipher").parentFile,
                    "growurdhan-v9.sqlcipher.restore-" + UUID.randomUUID()).apply {
                    writeBytes(byteArrayOf(7, 8, 9))
                }
                else -> File(ctx.getDatabasePath("growurdhan-v9.sqlcipher").parentFile,
                    "growurdhan-v9.sqlcipher.restore-" + UUID.randomUUID() + "-wal").apply {
                    writeBytes(byteArrayOf(10, 11))
                }
            }
            val markerBytes = marker.readBytes()
            assertThrows(IllegalStateException::class.java) {
                RestoreArtifactGuard.requireNoAbandonedRestore(ctx)
            }
            assertThrows(IllegalStateException::class.java) {
                ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                    credentials, explicitOwnerApproval = true)
            }
            assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
            assertFalse(File(ctx.noBackupFilesDir, "vault-key.v1").exists())
            assertArrayEquals(pendingBytes, pending.readBytes())
            assertArrayEquals(markerBytes, marker.readBytes())

            // Simulate manual removal of the pending archive only. The
            // orphan itself must continue to prevent empty book creation.
            assertTrue(pending.delete())
            assertThrows(IllegalStateException::class.java) {
                EncryptedVaultStore(ctx).createFresh(false).close()
            }
            assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
            assertArrayEquals(markerBytes, marker.readBytes())
        }
    }

    @Test fun readOnlyOwnerRecoveryInspectionHandlesEveryPublicationBoundary() {
        val (archive, credentials) = newSyntheticEncryptedBackup(
            true, "forensic@example.invalid", "synthetic-recovery-only")
        val cases = listOf(
            ApprovedNewVaultRestore.Boundary.BEFORE_CLAIM to
                RestoreRecoveryInspector.State.AMBIGUOUS,
            ApprovedNewVaultRestore.Boundary.AFTER_CLAIM to
                RestoreRecoveryInspector.State.CLAIM_ONLY,
            ApprovedNewVaultRestore.Boundary.AFTER_KEY_CREATION to
                RestoreRecoveryInspector.State.INCOMPLETE,
            ApprovedNewVaultRestore.Boundary.AFTER_CANDIDATE_VERIFIED to
                RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
            ApprovedNewVaultRestore.Boundary.BEFORE_KEY_PUBLICATION to
                RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
            ApprovedNewVaultRestore.Boundary.AFTER_KEY_PUBLICATION to
                RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
            ApprovedNewVaultRestore.Boundary.BEFORE_DATABASE_PUBLICATION to
                RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
            ApprovedNewVaultRestore.Boundary.AFTER_DATABASE_PUBLICATION to
                RestoreRecoveryInspector.State.VERIFIED_LIVE
        )
        for ((boundary, expected) in cases) {
            val ctx = sandbox()
            LockedRestoreStaging(ctx).stageEncryptedArchiveOnce(archive)
            var reached = false
            assertThrows(IllegalStateException::class.java) {
                ApprovedNewVaultRestore(ctx) { point ->
                    if (point == boundary) {
                        reached = true
                        throw IllegalStateException("Synthetic crash injection")
                    }
                }.restoreFromStagedNewVault(credentials, explicitOwnerApproval = true)
            }
            assertTrue("Restore should reach injected $boundary", reached)
            fun snapshot(): Map<String, ByteArray> {
                val folders = listOf(ctx.noBackupFilesDir,
                    ctx.getDatabasePath("growurdhan-v9.sqlcipher").parentFile!!)
                return folders.flatMap { root ->
                    root.walkTopDown().filter { it.isFile }.map {
                        it.absolutePath to it.readBytes()
                    }.toList()
                }.toMap()
            }
            val before = snapshot()
            val inspector = RestoreRecoveryInspector(ctx)
            assertThrows(IllegalStateException::class.java) {
                inspector.inspectForOwnerReview(credentials, explicitOwnerApproval = false)
            }
            assertThrows(Exception::class.java) {
                inspector.inspectForOwnerReview(
                    credentials.copy(password = "incorrect-password"),
                    explicitOwnerApproval = true)
            }
            val first = inspector.inspectForOwnerReview(credentials, explicitOwnerApproval = true)
            val second = RestoreRecoveryInspector(ctx).inspectForOwnerReview(
                credentials, explicitOwnerApproval = true)
            assertEquals(boundary.toString(), expected, first.state)
            assertEquals(first, second)
            if (expected == RestoreRecoveryInspector.State.VERIFIED_CANDIDATE ||
                expected == RestoreRecoveryInspector.State.VERIFIED_LIVE) {
                assertEquals(8, first.verifiedRows)
                assertTrue(first.durableClaimPresent)
            }
            val after = snapshot()
            assertEquals(before.keys, after.keys)
            for (file in before.keys)
                assertArrayEquals("Recovery review must not modify $boundary", before[file], after[file])
            // Even a fully verified candidate or a completed live vault is NOT
            // permission to bypass the immutable replay claim.
            if (boundary != ApprovedNewVaultRestore.Boundary.BEFORE_CLAIM) {
                assertThrows(Exception::class.java) {
                    ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                        credentials, explicitOwnerApproval = true)
                }
            }
        }
    }

    @Test fun ownerRecoveryRejectsUnmatchedKeysChangedRowsAndCorruptCipherWithoutCleanup() {
        val (archive, credentials) = newSyntheticEncryptedBackup(
            false, "forensic-legacy@example.invalid", "synthetic-legacy-password")
        fun interrupted(): Context {
            val ctx = sandbox()
            LockedRestoreStaging(ctx).stageEncryptedArchiveOnce(archive)
            var reached = false
            assertThrows(IllegalStateException::class.java) {
                ApprovedNewVaultRestore(ctx) { point ->
                    if (point == ApprovedNewVaultRestore.Boundary.AFTER_CANDIDATE_VERIFIED) {
                        reached = true
                        throw IllegalStateException("Synthetic interruption after verification")
                    }
                }.restoreFromStagedNewVault(credentials, explicitOwnerApproval = true)
            }
            assertTrue(reached)
            assertEquals(RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
                RestoreRecoveryInspector(ctx).inspectForOwnerReview(
                    credentials, explicitOwnerApproval = true).state)
            return ctx
        }
        val mismatched = interrupted()
        val originalKey = mismatched.noBackupFilesDir.listFiles()!!.single {
            it.name.startsWith("vault-key.v1.restore-")
        }
        val switched = File(mismatched.noBackupFilesDir,
            "vault-key.v1.restore-" + UUID.randomUUID())
        assertTrue(originalKey.renameTo(switched))
        val switchedBytes = switched.readBytes()
        val rejected = RestoreRecoveryInspector(mismatched).inspectForOwnerReview(
            credentials, explicitOwnerApproval = true)
        assertEquals(RestoreRecoveryInspector.State.AMBIGUOUS, rejected.state)
        assertArrayEquals(switchedBytes, switched.readBytes())

        val changedRows = interrupted()
        val candidate = changedRows.getDatabasePath("growurdhan-v9.sqlcipher")
            .parentFile!!.listFiles()!!.single { it.name.startsWith(
                "growurdhan-v9.sqlcipher.restore-") }
        val keyFile = changedRows.noBackupFilesDir.listFiles()!!.single {
            it.name.startsWith("vault-key.v1.restore-")
        }
        val dbKey = KeystoreWrappedVaultKey(changedRows, keyFile.name).unwrapExisting()
        try {
            SQLiteDatabase.openDatabase(candidate.absolutePath, dbKey, null,
                SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { _, _ -> throw IllegalStateException("Synthetic corruption") },
                null).use { db ->
                db.execSQL("UPDATE entity SET display_name='ALTERED SYNTHETIC' WHERE entity_id='owner'")
            }
        } finally { dbKey.fill(0) }
        val tamperedBytes = candidate.readBytes()
        assertThrows(Exception::class.java) {
            RestoreRecoveryInspector(changedRows).inspectForOwnerReview(
                credentials, explicitOwnerApproval = true)
        }
        assertArrayEquals(tamperedBytes, candidate.readBytes())

        val corrupted = interrupted()
        val encrypted = corrupted.getDatabasePath("growurdhan-v9.sqlcipher")
            .parentFile!!.listFiles()!!.single { it.name.startsWith(
                "growurdhan-v9.sqlcipher.restore-") }
        val wrongCipherBytes = encrypted.readBytes().also {
            it[0] = (it[0].toInt() xor 1).toByte()
        }
        encrypted.writeBytes(wrongCipherBytes)
        assertThrows(Exception::class.java) {
            RestoreRecoveryInspector(corrupted).inspectForOwnerReview(
                credentials, explicitOwnerApproval = true)
        }
        assertArrayEquals(wrongCipherBytes, encrypted.readBytes())
        assertFalse(corrupted.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

    @Test fun lowStorageFaultInjectionPreservesClaimAndOrphanedPublicationEvidence() {
        val (archive, credentials) = newSyntheticEncryptedBackup(
            true, "lowspace@example.invalid", "synthetic-lowspace-pass")
        for (boundary in listOf(
            ApprovedNewVaultRestore.Boundary.AFTER_CLAIM,
            ApprovedNewVaultRestore.Boundary.BEFORE_DATABASE_PUBLICATION
        )) {
            val ctx = sandbox()
            LockedRestoreStaging(ctx).stageEncryptedArchiveOnce(archive)
            val pending = File(ctx.noBackupFilesDir, "restore-pending.v1.kosha")
            val originalPending = pending.readBytes()
            var reached = false
            assertThrows(IOException::class.java) {
                ApprovedNewVaultRestore(ctx) { point ->
                    if (point == boundary) {
                        reached = true
                        throw IOException("Synthetic storage exhaustion (no actual disk filled)")
                    }
                }.restoreFromStagedNewVault(credentials, explicitOwnerApproval = true)
            }
            assertTrue(reached)
            assertArrayEquals(originalPending, pending.readBytes())
            val claimsDir = File(ctx.noBackupFilesDir, "restore-claims")
            assertEquals(1, claimsDir.listFiles()!!.size)
            val claim = claimsDir.listFiles()!!.single()
            val claimBytes = claim.readBytes()
            val inspection = RestoreRecoveryInspector(ctx).inspectForOwnerReview(
                credentials, explicitOwnerApproval = true)
            assertEquals(
                if (boundary == ApprovedNewVaultRestore.Boundary.AFTER_CLAIM)
                    RestoreRecoveryInspector.State.CLAIM_ONLY
                else RestoreRecoveryInspector.State.VERIFIED_CANDIDATE,
                inspection.state)
            assertArrayEquals(claimBytes, claim.readBytes())
            assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
            assertThrows(Exception::class.java) {
                ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                    credentials, explicitOwnerApproval = true)
            }
            assertArrayEquals(originalPending, pending.readBytes())
        }
    }

    @Test fun genuineBinaryGcmStagingPublishesOnlyNewEncryptedVaultAndRejectsReplay() {
        val ctx = sandbox()
        val (archive, credentials) = newSyntheticEncryptedBackup(
            true, "owner@example.invalid", "synthetic-not-a-secret")
        val staging = LockedRestoreStaging(ctx)
        val receipt = staging.stageEncryptedArchiveOnce(archive)
        val wrong = credentials.copy(password = "incorrect-credential")
        assertThrows(Exception::class.java) {
            ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                wrong, explicitOwnerApproval = true)
        }
        assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertThrows(IllegalStateException::class.java) {
            ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                credentials, explicitOwnerApproval = false)
        }
        val result = ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
            credentials, explicitOwnerApproval = true)
        assertEquals(8, result.restoredRows)
        assertTrue(result.authenticatedPlaintextSha256.matches(
            Regex("[0-9a-f]{64}")))
        assertEquals(archive.size, receipt.byteCount)
        val file = ctx.getDatabasePath("growurdhan-v9.sqlcipher")
        assertTrue(file.isFile)
        assertFalse(file.readBytes().copyOfRange(0, 16).contentEquals(
            "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)))
        EncryptedVaultStore(ctx).openExisting().use { db ->
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM ledger_event"))
            assertEquals(9007199254740993L,
                scalar(db, "SELECT total_amount_minor FROM ledger_event"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM entity"))
        }
        assertTrue(RestoreOnceJournal(File(ctx.noBackupFilesDir, "restore-claims")
            .toPath()).hasClaim(result.authenticatedPlaintextSha256))
        val bytes = file.readBytes()
        // A completely separate candidate must NEVER overwrite the already
        // published financial vault, even if publication is called directly.
        val unrelated = File(file.parentFile, "unrelated-synthetic-candidate")
        unrelated.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThrows(Exception::class.java) {
            EncryptedRestorePublisher.publishReservedAtomic(
                unrelated.toPath(), file.toPath())
        }
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(unrelated.exists()) // failed attempt preserves forensic source
        assertThrows(Exception::class.java) {
            ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
                credentials, explicitOwnerApproval = true)
        }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun syntheticKosha1GcmAndTamperMustNotCreateOrOverwriteVault() {
        val (archive, credentials) = newSyntheticEncryptedBackup(
            false, "older@example.invalid", "synthetic-not-a-secret")
        val ctx = sandbox()
        LockedRestoreStaging(ctx).stageEncryptedArchiveOnce(archive)
        val r = ApprovedNewVaultRestore(ctx).restoreFromStagedNewVault(
            credentials, explicitOwnerApproval = true)
        assertEquals(8, r.restoredRows)
        EncryptedVaultStore(ctx).openExisting().use { db ->
            assertEquals(9007199254740993L,
                scalar(db, "SELECT total_amount_minor FROM ledger_event"))
        }
        val tamperContext = sandbox()
        val wrapped = JSONObject(String(archive, Charsets.UTF_8))
        val ciphertext = wrapped.getString("cipherB64")
        wrapped.put("cipherB64",
            (if (ciphertext[0] == 'A') "B" else "A") + ciphertext.substring(1))
        val changed = wrapped.toString().toByteArray(Charsets.UTF_8)
        LockedRestoreStaging(tamperContext).stageEncryptedArchiveOnce(changed)
        assertThrows(Exception::class.java) {
            ApprovedNewVaultRestore(tamperContext).restoreFromStagedNewVault(
                credentials, explicitOwnerApproval = true)
        }
        assertFalse(tamperContext.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

}
