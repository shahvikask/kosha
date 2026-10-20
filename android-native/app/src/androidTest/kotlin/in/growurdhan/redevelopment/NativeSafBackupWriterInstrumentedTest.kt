package `in`.growurdhan.redevelopment

import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.LegacyBackupEnvelopeWriter
import android.net.Uri
import java.io.ByteArrayInputStream
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NativeSafBackupWriterInstrumentedTest {
    private val account = "synthetic@example.invalid"
    private val password = "Synthetic!Password"
    private fun writer() = NativeSafEncryptedBackupWriter(
        InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun pruningOnlyConsidersThisAppExactEncryptedBackupNames() {
        fun entry(name: String, time: Long) =
            NativeSafEncryptedBackupWriter.Entry(name, name, time)
        val own = (1..7).map { i ->
            entry("GrowUrDhan-Native-20261020T00000" + i + "Z-00ab01fe.kosha",
                i.toLong())
        }
        val all = own + listOf(
            entry("Family-20261020.kosha", 0L),
            entry("GrowUrDhan-Native-20261020T000001Z-00ab01fe.kosha.pending", 0L),
            entry("GrowUrDhan-Native-old.kosha", 0L))
        val prune = NativeSafEncryptedBackupWriter.obsolete(all)
        assertEquals(2, prune.size)
        assertTrue(prune.all { it.name in own.map { e -> e.name } })
        assertEquals(1L, prune.minOf { it.lastModified })
        assertEquals(2L, prune.maxOf { it.lastModified })
    }

    @Test fun generatedNameHasNoEmailPanOrOtherPersonalIdentifiers() {
        val name = NativeSafEncryptedBackupWriter.newName(
            Instant.parse("2026-10-20T10:20:30Z"))
        assertTrue(name.startsWith("GrowUrDhan-Native-20261020T102030Z-"))
        assertTrue(name.endsWith(".kosha"))
        assertFalse(name.contains("synthetic"))
    }

    @Test fun encryptedReadbackRejectsSameLengthCorruptionTruncationAndSuffix() {
        val source = ByteArray(100_031) { ((it * 97 + 19) % 251).toByte() }
        val target = writer()
        target.verifyEncryptedReadback(ByteArrayInputStream(source.copyOf()), source)
        val sameSizeTampered = source.copyOf().also {
            it[it.size / 2] = (it[it.size / 2].toInt() xor 0x40).toByte()
        }
        assertThrows(IllegalArgumentException::class.java) {
            target.verifyEncryptedReadback(ByteArrayInputStream(sameSizeTampered), source)
        }
        assertThrows(IllegalArgumentException::class.java) {
            target.verifyEncryptedReadback(ByteArrayInputStream(source.copyOfRange(
                0, source.size - 1)), source)
        }
        assertThrows(IllegalArgumentException::class.java) {
            target.verifyEncryptedReadback(ByteArrayInputStream(source + 0x7f.toByte()), source)
        }
    }

    @Test fun encryptedReadbackCannotMistakeProviderEmptyResponseForSuccess() {
        val encrypted = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            """{"kind":"GUDVAULT","version":2,"account":{"email":"synthetic@example.invalid"},"familyMembers":[],"holdings":[],"ledgerEvents":[]}""",
            account, password)
        assertThrows(IllegalArgumentException::class.java) {
            writer().verifyEncryptedReadback(ByteArrayInputStream(byteArrayOf()), encrypted)
        }
        writer().verifyEncryptedReadback(ByteArrayInputStream(encrypted), encrypted)
    }

    @Test fun wrongPasswordAndUnapprovedOrUnpermittedDestinationFailBeforeAnyWrite() {
        val original = """{"kind":"GUDVAULT","version":2,"account":{"email":"synthetic@example.invalid"},"familyMembers":[],"holdings":[],"ledgerEvents":[]}"""
        val encrypted = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            original, account, password)
        assertThrows(IllegalArgumentException::class.java) {
            writer().publish(account, password, Uri.EMPTY, encrypted, false, true)
        }
        assertThrows(Exception::class.java) {
            writer().publish(account, "wrong password", Uri.EMPTY, encrypted, true, true)
        }
        assertThrows(Exception::class.java) {
            writer().publish(account, password, Uri.parse("content://no.grant/tree/unsupported"),
                encrypted, true, true)
        }
        assertThrows(Exception::class.java) {
            writer().publish(account, password, Uri.EMPTY,
                "plaintext financial backup".toByteArray(), true, true)
        }
    }
}
