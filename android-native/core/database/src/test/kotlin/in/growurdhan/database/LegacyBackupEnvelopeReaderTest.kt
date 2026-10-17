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
    }
}
