package `in`.growurdhan.database

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

/**
 * M24 synthetic CONTRACT GOLDENS for frozen backup.ts and vault.ts.
 *
 * This is not a production restore/decoder, does not persist keys or customer
 * data, and does not assert M25 identity or live Android compatibility.
 * In particular, old KOSHA1 is password-only; GUDVLT01 binds email + password.
 * This test harness enforces extra bounds and local replay staging; the frozen
 * original does not implement a replay ledger. Do NOT infer product support.
 */
class FrozenLegacyBackupEnvelopeGoldenTest {
    private val magic = "GUDVLT01".toByteArray(StandardCharsets.US_ASCII)
    private val iterations = 600_000
    private val salt = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (it + 41).toByte() }
    private val email = " synthetic@example.invalid "
    private val password = "only-a-test-password"

    private val syntheticSnapshot =
        """{"kind":"GUDVAULT","version":2,"exportedAt":"2026-10-16T00:00:00Z","account":{"email":"synthetic@example.invalid"},"familyMembers":[{"id":"owner-A","role":"Self"}],"holdings":[{"id":"holding-A","ownerId":"owner-A","unknownNested":{"preserve":[1,true,{"x":"opaque"}]}}],"ledgerEvents":[{"ownerId":"owner-A","quantityScaled":"1234567890123","totalAmountMinor":"9007199254740993"}],"accounts":[{"id":"account-A","ownerId":"owner-A"}],"goals":[],"casLatest":null}"""

    private fun frozenFile(path: String): String {
        var directory = File(System.getProperty("user.dir"))
        repeat(10) {
            val file = File(directory, path)
            if (file.isFile) return file.readText()
            directory = directory.parentFile ?: error("Frozen source file not found: " + path)
        }
        error("Frozen source file not found: " + path)
    }

    private fun derive(material: String, saltBytes: ByteArray, rounds: Int): SecretKeySpec {
        require(rounds == iterations) { "Only the frozen current fixture round count is allowed." }
        require(saltBytes.size == 16)
        val specification = PBEKeySpec(material.toCharArray(), saltBytes, rounds, 256)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification).encoded
            SecretKeySpec(key, "AES")
        } finally {
            specification.clearPassword()
        }
    }

    private fun encrypt(plain: String, material: String, saltBytes: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(material, saltBytes, iterations),
            GCMParameterSpec(128, nonce))
        return cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decrypt(blob: ByteArray, material: String, saltBytes: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(material, saltBytes, iterations),
            GCMParameterSpec(128, nonce))
        return String(cipher.doFinal(blob), StandardCharsets.UTF_8)
    }

    private fun material(address: String, pass: String) =
        "GrowUrDhan\n" + address.trim().lowercase() + "\n" + pass

    private fun binaryEnvelope(plaintext: String, address: String = email,
                               pass: String = password, nonce: ByteArray = iv): ByteArray {
        val ciphertext = encrypt(plaintext, material(address, pass), salt, nonce)
        return ByteBuffer.allocate(40 + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
            .put(magic).putInt(iterations).put(salt).put(nonce).put(ciphertext).array()
    }

    private fun unpackBinary(bytes: ByteArray, address: String, pass: String): String {
        require(bytes.size >= 56) { "Truncated AES-GCM binary backup." }
        require(bytes.copyOfRange(0, 8).contentEquals(magic)) { "Wrong backup magic." }
        val rounds = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.BIG_ENDIAN).int
        require(rounds == iterations) { "Unsafe or unsupported KDF iteration count." }
        return decrypt(bytes.copyOfRange(40, bytes.size),
            material(address, pass), bytes.copyOfRange(12, 28), bytes.copyOfRange(28, 40))
    }

    /** Test-only, in-memory replay check; does NOT exist in production or frozen backup.ts. */
    private class ProposedReplayGate {
        private val committed = mutableSetOf<String>()
        fun accepted(snapshot: String, identityApproved: Boolean): Boolean {
            if (!identityApproved) return false
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(snapshot.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return committed.add(digest)
        }
        fun size(): Int = committed.size
    }

    @Test fun frozenBackupAndLegacySourceContractHasNotDrifted() {
        val original = frozenFile("src/lib/backup.ts")
        val vault = frozenFile("src/lib/vault.ts")
        val migration = frozenFile("src/db/migrate.ts")
        assertTrue(original.contains("const MAGIC = 'GUDVLT01'"))
        assertTrue(original.contains("const PBKDF2_ITERATIONS = 600_000"))
        assertTrue(original.contains("const SALT_BYTES = 16"))
        assertTrue(original.contains("const IV_BYTES = 12"))
        assertTrue(original.contains("setUint32(8, PBKDF2_ITERATIONS, false)"))
        assertTrue(original.contains("out.set(salt, 12)"))
        assertTrue(original.contains("out.set(iv, 28)"))
        assertTrue(original.contains("out.set(cipher, 40)"))
        assertTrue(original.contains("email.trim().toLowerCase()"))
        assertTrue(original.contains("blob.magic !== 'KOSHA1'"))
        assertTrue(original.contains("deriveKey(password, salt, blob.iterations"))
        assertTrue(original.contains("if (!inner.account)"))
        assertTrue(vault.contains("const MAGIC = 'KOSHA1'"))
        assertTrue(migration.contains("__pre_sqlite_backup"))
        assertTrue(migration.contains("valueBeforeMinor"))
        assertTrue(migration.contains("valueAfterMinor"))
        assertTrue(migration.contains("if (!report.verified)"))
    }

    @Test fun binaryV2EnvelopeRoundTripRetainsLargeIntegersOwnerAndUnknownFields() {
        val bytes = binaryEnvelope(syntheticSnapshot)
        assertEquals("GUDVLT01", String(bytes.copyOfRange(0, 8), StandardCharsets.US_ASCII))
        assertEquals(iterations, ByteBuffer.wrap(bytes, 8, 4).int)
        assertArrayEquals(salt, bytes.copyOfRange(12, 28))
        assertArrayEquals(iv, bytes.copyOfRange(28, 40))
        assertFalse(String(bytes, StandardCharsets.ISO_8859_1).contains("holding-A"))
        assertEquals(syntheticSnapshot,
            unpackBinary(bytes, "SYNTHETIC@example.invalid", password))
        assertTrue(unpackBinary(bytes, email, password).contains("9007199254740993"))
        assertTrue(unpackBinary(bytes, email, password).contains("unknownNested"))
    }

    @Test fun encryptedEnvelopeRejectsTamperWrongIdentityAndInvalidHeaders() {
        val blob = binaryEnvelope(syntheticSnapshot)
        assertThrows(Exception::class.java) {
            unpackBinary(blob, "another@example.invalid", password)
        }
        assertThrows(Exception::class.java) { unpackBinary(blob, email, "wrong-password") }
        val altered = blob.clone()
        altered[altered.lastIndex] = (altered.last().toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) { unpackBinary(altered, email, password) }
        val invalidIterations = blob.clone()
        invalidIterations[11] = 0
        assertThrows(IllegalArgumentException::class.java) {
            unpackBinary(invalidIterations, email, password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            unpackBinary(blob.copyOfRange(0, 42), email, password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            unpackBinary("KOSHA1".toByteArray(), email, password)
        }
    }

    @Test fun oldKosha1JsonEnvelopeUsesPasswordOnlyAndRequiresEmbeddedAccount() {
        // The old envelope is JSON containing Base64 salt/iv/cipher. We exercise
        // its exact wire fields, not a finished JSON backup importer.
        val cipher = encrypt(syntheticSnapshot, password, salt, iv)
        val encoder = Base64.getEncoder()
        val legacyJson = """{"magic":"KOSHA1","kdf":"PBKDF2-SHA256","iterations":600000,"saltB64":"""" +
            encoder.encodeToString(salt) + """","ivB64":"""" +
            encoder.encodeToString(iv) + """","cipherB64":"""" +
            encoder.encodeToString(cipher) + "\"}"
        assertTrue(legacyJson.startsWith("""{"magic":"KOSHA1""""))
        assertTrue(legacyJson.contains("\"cipherB64\""))
        assertEquals(syntheticSnapshot, decrypt(
            decoderField(legacyJson, "cipherB64"), password,
            decoderField(legacyJson, "saltB64"), decoderField(legacyJson, "ivB64")))
        assertThrows(Exception::class.java) {
            decrypt(cipher, material(email, password), salt, iv)
        }
        val missingAccount = """{"kind":"GUDVAULT","version":1,"holdings":[]}"""
        val ciphertext = encrypt(missingAccount, password, salt, iv)
        assertFalse(decrypt(ciphertext, password, salt, iv).contains("\"account\""))
        assertTrue(frozenFile("src/lib/backup.ts").contains(
            "This older backup has no login."))
    }

    private fun decoderField(json: String, key: String): ByteArray {
        // Synthetic known-field test only. NOT a general JSON decoder.
        val match = Regex("\"" + key + "\":\"([A-Za-z0-9+/=]+)\"").find(json)
            ?: error("Missing synthetic wire field " + key)
        return Base64.getDecoder().decode(match.groupValues[1])
    }

    @Test fun proposedTwoPhaseReplayAndFailureStagingNeverMarksUnapprovedBackups() {
        val gate = ProposedReplayGate()
        val original = binaryEnvelope(syntheticSnapshot)
        assertFalse(gate.accepted(syntheticSnapshot, false))
        assertEquals(0, gate.size())
        val corrupt = original.clone()
        corrupt[45] = (corrupt[45].toInt() xor 7).toByte()
        assertThrows(Exception::class.java) { unpackBinary(corrupt, email, password) }
        assertEquals(0, gate.size())
        val verified = unpackBinary(original, email, password)
        assertTrue(gate.accepted(verified, true))
        assertEquals(1, gate.size())
        // A newly encrypted backup of the same snapshot must not be silently
        // counted as an independent restore in the proposed future policy.
        val secondNonce = ByteArray(12) { (it + 11).toByte() }
        val duplicate = binaryEnvelope(syntheticSnapshot, nonce = secondNonce)
        assertFalse(original.contentEquals(duplicate))
        assertFalse(gate.accepted(unpackBinary(duplicate, email, password), true))
        assertEquals(1, gate.size())
    }
}
