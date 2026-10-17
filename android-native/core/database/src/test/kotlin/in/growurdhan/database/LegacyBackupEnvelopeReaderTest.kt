package `in`.growurdhan.database

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

/** Synthetic interoperability checks; no captured personal or historical user backup. */
class LegacyBackupEnvelopeReaderTest {
    private val json = """{"kind":"GUDVAULT","version":2,"account":{"email":"m24@example.invalid"},"familyMembers":[{"id":"owner-A"}],"holdings":[{"ownerId":"owner-A","opaque":{"unknown":[true,"keep"]}}],"ledgerEvents":[{"quantityScaled":"1234567890123","totalAmountMinor":"9007199254740993"}]}"""
    private val salt = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (it + 1).toByte() }
    private val password = "local-only-test-password"

    private fun encrypt(material: String): ByteArray {
        val spec = PBEKeySpec(material.toCharArray(), salt, 600_000, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, iv))
            return cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
        } finally { key.fill(0) }
    }

    /** auth.ts account hashes use 210k, unlike backup.ts envelope's 600k. */
    private fun authHashAt210k(secret: String): Pair<String, String> {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 210_000, 256)
        val raw = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return try {
            Base64.getEncoder().encodeToString(salt) to
                Base64.getEncoder().encodeToString(raw)
        } finally { raw.fill(0) }
    }

    private fun modern(): ByteArray {
        val blob = encrypt("GrowUrDhan\nm24@example.invalid\n" + password)
        return ByteBuffer.allocate(40 + blob.size).order(ByteOrder.BIG_ENDIAN)
            .put("GUDVLT01".toByteArray(StandardCharsets.US_ASCII))
            .putInt(600_000).put(salt).put(iv).put(blob).array()
    }

    @Test fun readerPreservesBigIntegersOwnersAndUnknownJsonVerbatim() {
        assertEquals(json, LegacyBackupEnvelopeReader.decodeGUDVLT01(
            modern(), " M24@EXAMPLE.INVALID ", password))
        val login = authHashAt210k(password)
        val first = authHashAt210k("first city")
        val second = authHashAt210k("blue sea")
        val account = RestoreIdentityGate.Account(
            "m24@example.invalid", login.first, login.second, 210_000,
            "ABCDE1234F", "ABCDE****F", "1990-01-01", "city",
            "${first.first}:${first.second}:210000", "pet",
            "${second.first}:${second.second}:210000"
        )
        val valid = RestoreIdentityGate.Credentials(
            " M24@EXAMPLE.INVALID ", password, "ABCDE1234F", "1990-01-01",
            "city", "First City", "pet", " Blue   Sea "
        )
        RestoreIdentityGate.verifyForNewDevice(account, valid)
        assertThrows(IllegalArgumentException::class.java) {
            RestoreIdentityGate.verifyForNewDevice(account,
                valid.copy(q2Answer = "wrong"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreIdentityGate.verifyForNewDevice(account.copy(
                passwordIterations = 600_000), valid)
        }
        // Restore preflight is deliberately independent from decryption:
        // it cannot synthesize a missing ledger ID, owner or cash flow.
        val complete = RestoreLedgerPreflight.Snapshot(
            owners = listOf(RestoreLedgerPreflight.Owner("owner-A")),
            holdings = listOf(RestoreLedgerPreflight.Holding(
                "holding-A", "owner-A", null, "1234567890123", "9007199254740993")),
            accounts = listOf(RestoreLedgerPreflight.Account("account-A", "owner-A")),
            goals = emptyList(),
            events = listOf(RestoreLedgerPreflight.Event(
                "event-A", "holding-A", "owner-A", "BUY", "2026-10-16",
                "1234567890123", "10000", "9007199254740993"))
        )
        assertEquals(1, RestoreLedgerPreflight.validate(complete).events)
        // The frozen positions.ts synthesizes a transient opening balance
        // for a positive holding with NO events. Native v9 has no quantity
        // column on holding: reject rather than silently zeroing the book
        // or inventing a cash-flow event during restore.
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(events = emptyList()))
        }
        // A wholly sold/empty legacy holding with zero quantity needs no
        // synthetic ledger row; retain its reference record unchanged.
        assertEquals(0, RestoreLedgerPreflight.validate(complete.copy(
            holdings = complete.holdings.map { it.copy(quantityScaled = "0") },
            events = emptyList())).events)
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                holdings = complete.holdings + RestoreLedgerPreflight.Holding(
                    "orphan-position", "owner-A", null, "500", "7")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                events = complete.events.map { it.copy(holdingId = "missing") }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                holdings = complete.holdings.map {
                    it.copy(currentPriceMinor = "9007199254740993.5") }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                holdings = complete.holdings.map {
                    it.copy(currentPriceMinor = "9223372036854775808") }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                events = complete.events.map { it.copy(eventType = "UNKNOWN") }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                events = complete.events.map { it.copy(eventType = "CORRECTION") }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(complete.copy(
                events = complete.events.map { it.copy(supersedesEventId = "event-A") }))
        }
    }

    @Test fun passwordOnlyKosha1RetainsSameExactPayload() {
        val b64 = Base64.getEncoder()
        val blob = LegacyBackupEnvelopeReader.Kosha1(
            "KOSHA1","PBKDF2-SHA256",600_000,
            b64.encodeToString(salt), b64.encodeToString(iv),
            b64.encodeToString(encrypt(password))
        )
        assertEquals(json, LegacyBackupEnvelopeReader.decodeKOSHA1(blob, password))
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeKOSHA1(blob, "wrong")
        }
    }

    @Test fun frozenKosha1AbsentMetadataAndZeroIterationsRemainAuthenticated() {
        val b64 = Base64.getEncoder()
        val encrypted = b64.encodeToString(encrypt(password))
        val legacyWithoutOptionalKdf = LegacyBackupEnvelopeReader.Kosha1(
            "KOSHA1", "", 0, b64.encodeToString(salt),
            b64.encodeToString(iv), encrypted
        )
        assertEquals(json, LegacyBackupEnvelopeReader.decodeKOSHA1(
            legacyWithoutOptionalKdf, password))
        // Frozen original also accepts explicit zero rounds with a supported
        // algorithm, but no alternative nonzero KDF cost or algorithm.
        assertEquals(json, LegacyBackupEnvelopeReader.decodeKOSHA1(
            legacyWithoutOptionalKdf.copy(kdf = "PBKDF2-SHA256"), password))
        for (invalid in listOf(
            legacyWithoutOptionalKdf.copy(kdf = "PBKDF2-SHA1"),
            legacyWithoutOptionalKdf.copy(iterations = 1),
            legacyWithoutOptionalKdf.copy(iterations = 599_999),
            legacyWithoutOptionalKdf.copy(iterations = 600_001),
            legacyWithoutOptionalKdf.copy(magic = "UNKNOWN")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LegacyBackupEnvelopeReader.decodeKOSHA1(invalid, password)
            }
        }
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeKOSHA1(
                legacyWithoutOptionalKdf, "wrong-password")
        }
        val brokenCipher = b64.getDecoder().decode(encrypted)
        brokenCipher[brokenCipher.lastIndex] =
            (brokenCipher.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeKOSHA1(
                legacyWithoutOptionalKdf.copy(cipherB64 =
                    b64.encodeToString(brokenCipher)), password)
        }
    }

    @Test fun wrongEmailWrongPasswordAndTamperingNeverYieldPlaintext() {
        val bytes = modern()
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(bytes, "other@example.invalid", password)
        }
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(bytes, "m24@example.invalid", "wrong")
        }
        val corrupted = bytes.clone()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(corrupted,
                "m24@example.invalid", password)
        }
    }

    @Test fun shortInvalidHeaderAndKdfParametersAreRejected() {
        val bytes = modern()
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(
                bytes.copyOfRange(0, 45), "m24@example.invalid", password)
        }
        val badMagic = bytes.clone()
        badMagic[0] = 0
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(
                badMagic, "m24@example.invalid", password)
        }
        // Bounded preflight must run before Base64 decode or PBKDF2.
        val oversizedLegacyField = LegacyBackupEnvelopeReader.Kosha1(
            "KOSHA1", "PBKDF2-SHA256", 600_000,
            "A".repeat(33), "AA==", "AA=="
        )
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeReader.decodeKOSHA1(oversizedLegacyField, password)
        }
        val badRounds = bytes.clone()
        badRounds[11] = 0
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeReader.decodeGUDVLT01(
                badRounds, "m24@example.invalid", password)
        }

        // Persistently claim an authenticated archive once. A restart,
        // duplicate restore, malformed digest and missing directory must
        // all fail closed; no code here deletes or clears a replay claim.
        val root = java.nio.file.Files.createTempDirectory("m24-replay-")
        try {
            val digest = "a".repeat(64)
            val journal = RestoreOnceJournal(root)
            assertFalse(journal.hasClaim(digest))
            journal.claimOnce(digest)
            assertTrue(RestoreOnceJournal(root).hasClaim(digest))
            assertThrows(IllegalStateException::class.java) {
                journal.claimOnce(digest)
            }
            assertThrows(IllegalArgumentException::class.java) {
                journal.claimOnce("../invalid")
            }
            assertThrows(IllegalArgumentException::class.java) {
                RestoreOnceJournal(root.resolve("missing")).claimOnce("b".repeat(64))
            }
            assertEquals("GUD_RESTORE_CLAIM_V1\n" + digest + "\n",
                java.nio.file.Files.readString(root.resolve("restore-" + digest + ".claim")))
        } finally {
            java.nio.file.Files.walk(root).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach { java.nio.file.Files.deleteIfExists(it) }
            }
        }

        // An already-established SQLCipher file must never be replaced by
        // an unreviewed restore. createLink is exclusive across publishers.
        val dir = java.nio.file.Files.createTempDirectory("m24-vault-publish-")
        try {
            val stagedDb = dir.resolve("verified.sqlcipher")
            val liveDb = dir.resolve("growurdhan-v9.sqlcipher")
            val original = ByteArray(128) { (it + 1).toByte() }
            java.nio.file.Files.write(stagedDb, original)
            EncryptedRestorePublisher.publishExclusive(stagedDb, liveDb)
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(liveDb))
            assertFalse(java.nio.file.Files.exists(stagedDb))
            java.nio.file.Files.write(stagedDb, ByteArray(128) { 42 })
            assertThrows(IllegalStateException::class.java) {
                EncryptedRestorePublisher.publishExclusive(stagedDb, liveDb)
            }
            assertArrayEquals(original, java.nio.file.Files.readAllBytes(liveDb))
            val withWal = dir.resolve("uncheckpointed.sqlcipher")
            java.nio.file.Files.write(withWal, original)
            java.nio.file.Files.write(dir.resolve("uncheckpointed.sqlcipher-wal"), byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) {
                EncryptedRestorePublisher.publishExclusive(withWal, dir.resolve("unused.sqlcipher"))
            }
            assertFalse(java.nio.file.Files.exists(dir.resolve("unused.sqlcipher")))
        } finally {
            java.nio.file.Files.walk(dir).use { files ->
                files.sorted(java.util.Comparator.reverseOrder())
                    .forEach { java.nio.file.Files.deleteIfExists(it) }
            }
        }

        // This is a strict typed plan from synthetic COMPLETE source rows.
        // No missing holding or ledger fields may be inferred from the older
        // intentionally incomplete cryptographic compatibility fixture.
        val complete = RestoreV9Plan.Snapshot(
            "2026-10-16T00:00:00Z",
            RestoreV9Plan.Login("owner@example.invalid", "Synthetic", "Owner",
                "salt", "hash", 210_000, "2026-10-16", "city", "answer1",
                "pet", "answer2", "2026-10-16"),
            listOf(RestoreV9Plan.Family("owner-A", "Owner", "Self", true, "#000")),
            listOf(RestoreV9Plan.Account("account-A", "owner-A", "DEMAT", "Test")),
            listOf(RestoreV9Plan.Holding("holding-A", "owner-A", "account-A",
                "Synthetic Holding", "EQUITY", "Large cap", "TEST",
                "1234567890123", "9007199254740993", "2026-10-16", "NSE")),
            listOf(RestoreV9Plan.Event("event-A", "holding-A", "Synthetic Holding",
                "owner-A", "BUY", "2026-10-16", "1234567890123", "3",
                "9007199254740993", "0", "2026-10-16")),
            listOf(RestoreV9Plan.Goal("goal-A", "owner-A", "Retirement",
                "100000", "2030-10-16", "2026-10-16")),
            """{"kind":"GUDVAULT","unknown":{"preserve":true}}"""
        )
        val insertPlan = RestoreV9Plan.project(complete)
        assertEquals(8, insertPlan.rows.size)
        assertEquals(9007199254740993L,
            insertPlan.rows.single { it.table == "ledger_event" }
                .values.first { it == 9007199254740993L })
        assertEquals(complete.authenticatedOriginalJson, insertPlan.rows.last().values.last())

        val metadataPlan = RestoreV9Plan.project(complete.copy(
            accounts = complete.accounts.map { it.copy(
                dpId = "DP-TEST", clientId = "CLIENT-TEST",
                boId = "BO-TEST", nominee = "Synthetic nominee") },
            holdings = complete.holdings.map { it.copy(
                isin = "INE000SYNTH0", notes = "Original holding note", needsReview = true) },
            events = complete.events.map { it.copy(
                importBatchId = "source-batch", sourceRef = "source-reference",
                notes = "Original ledger note") },
            goals = complete.goals.map { it.copy(priority = "HIGH") }
        ))
        fun field(table: String, column: String): Any? {
            val row = metadataPlan.rows.single { it.table == table }
            return row.values[row.columns.indexOf(column).also { check(it >= 0) }]
        }
        assertEquals("DP-TEST", field("account", "dp_id"))
        assertEquals("CLIENT-TEST", field("account", "client_id"))
        assertEquals("BO-TEST", field("account", "bo_id"))
        assertEquals("Synthetic nominee", field("account", "nominee"))
        assertEquals("INE000SYNTH0", field("instrument", "isin"))
        assertEquals(1L, field("holding", "needs_review"))
        assertEquals("Original holding note", field("holding", "notes"))
        assertEquals("source-batch", field("ledger_event", "import_batch_id"))
        assertEquals("source-reference", field("ledger_event", "source_ref"))
        assertEquals("Original ledger note", field("ledger_event", "notes"))
        assertEquals("HIGH", field("goal", "priority"))
        assertEquals(1, metadataPlan.rows.count { it.table == "ledger_event" })

        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(complete.copy(events = complete.events.map {
                it.copy(holdingName = "invented")
            }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreV9Plan.project(complete.copy(holdings = complete.holdings.map {
                it.copy(currentPriceMinor = "9007199254740993.5")
            }))
        }
        class FakeFreshTarget : RestoreV9InsertTransaction.EmptyEncryptedTarget {
            var pending = 0
            var persisted = 0
            var marked = false
            var ended = false
            var failAt = -1
            override fun verifyNewEmptyCipherTarget() { check(persisted == 0) }
            override fun beginTransaction() {}
            override fun insert(table: String, columns: List<String>, parameters: List<Any?>) {
                if (pending == failAt) error("synthetic mid-transaction failure")
                pending++
            }
            override fun verifyTransactionIntegrity(expectedRows: Int) {
                check(pending == expectedRows)
            }
            override fun setTransactionSuccessful() { marked = true }
            override fun endTransaction() {
                if (marked) persisted += pending
                pending = 0
                ended = true
            }
        }
        val rolledBack = FakeFreshTarget().apply { failAt = 3 }
        assertThrows(IllegalStateException::class.java) {
            RestoreV9InsertTransaction.apply(insertPlan, rolledBack)
        }
        assertEquals(0, rolledBack.persisted)
        assertTrue(rolledBack.ended)
        val committed = FakeFreshTarget()
        RestoreV9InsertTransaction.apply(insertPlan, committed)
        assertEquals(8, committed.persisted)
    }
}
