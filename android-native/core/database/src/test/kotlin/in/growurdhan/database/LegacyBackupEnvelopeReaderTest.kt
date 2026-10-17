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
    }
}
