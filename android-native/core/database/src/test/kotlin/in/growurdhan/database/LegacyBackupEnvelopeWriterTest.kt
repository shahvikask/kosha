package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

class LegacyBackupEnvelopeWriterTest {
    private val original = """{"kind":"GUDVAULT","version":2,"exportedAt":"2026-10-19T00:00:00Z","account":{"email":"house@example.invalid"},"familyMembers":[],"holdings":[],"ledgerEvents":[],"accounts":[],"goals":[],"casLatest":null}"""
    private val email = "house@example.invalid"
    private val password = "Synthetic-not-a-user-secret!#2026"

    @Test fun canonicalBinaryEnvelopeDecryptsWithTheOriginalFrozenReader() {
        val one = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            original, "  HOUSE@EXAMPLE.INVALID ", password)
        assertEquals("GUDVLT01", String(one.copyOfRange(0, 8), Charsets.US_ASCII))
        assertEquals(600_000, java.nio.ByteBuffer.wrap(one, 8, 4).int)
        assertFalse(one.copyOfRange(40, one.size)
            .contentEquals(original.toByteArray(Charsets.UTF_8)))
        assertEquals(original, LegacyBackupEnvelopeReader.decodeGUDVLT01(
            one, email, password))
        val two = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            original, email, password)
        assertFalse("Fresh salt and nonce are required for every export",
            one.contentEquals(two))
        assertEquals(original, LegacyBackupEnvelopeReader.decodeGUDVLT01(
            two, email, password))
    }

    @Test fun wrongPasswordWrongOwnerAndCiphertextTamperingAllFailClosed() {
        val file = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            original, email, password)
        for (attempt in listOf(
            "intruder@example.invalid" to password,
            email to "incorrect-password"
        )) {
            assertThrows(Exception::class.java) {
                LegacyBackupEnvelopeReader.decodeGUDVLT01(file,
                    attempt.first, attempt.second)
            }
        }
        for (index in listOf(8, 12, 28, 40, file.lastIndex)) {
            val changed = file.copyOf()
            changed[index] = (changed[index].toInt() xor 1).toByte()
            assertThrows(Exception::class.java) {
                LegacyBackupEnvelopeReader.decodeGUDVLT01(changed, email, password)
            }
        }
    }

    @Test fun refusesEmptyInputsAndDoesNotProduceAnUnlockedJsonBackup() {
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeWriter.encryptAuthenticatedJson("", email, password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(original, "", password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(original, email, "")
        }
    }
}
