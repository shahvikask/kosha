package `in`.growurdhan.redevelopment

import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.LegacyBackupEnvelopeWriter
import android.net.Uri
import java.io.ByteArrayInputStream
import java.time.Instant
import org.json.JSONObject
import org.json.JSONArray
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
    private fun completeSyntheticV2(): JSONObject {
        val accountDetails = JSONObject()
            .put("email", account).put("firstName", "Synthetic")
            .put("lastName", "Owner").put("passwordSalt", "synthetic-salt")
            .put("passwordHash", "synthetic-hash")
            .put("passwordIterations", 210000)
            .put("passwordChangedAt", "2026-10-20")
            .put("q1Id", "city").put("q1Hash", "synthetic-question-one")
            .put("q2Id", "pet").put("q2Hash", "synthetic-question-two")
            .put("createdAt", "2026-10-20")
            .put("panForStatements", "ABCDE1234F")
            .put("dateOfBirth", "1990-01-01")
        return JSONObject().put("kind", "GUDVAULT").put("version", 2)
            .put("exportedAt", "2026-10-20T00:00:00Z")
            .put("account", accountDetails)
            .put("familyMembers", JSONArray().put(
                JSONObject().put("id", "owner").put("name", "Synthetic")
                    .put("relation", "Self").put("isPrimary", true)))
            .put("accounts", JSONArray())
            .put("holdings", JSONArray())
            .put("ledgerEvents", JSONArray())
            .put("goals", JSONArray())
            .put("casLatest", JSONObject.NULL)
    }

    @Test fun authenticatedFullFrozenSnapshotPassesStructuralRecoveryPreflight() {
        val archive = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            completeSyntheticV2().toString(), account, password)
        writer().validateEncryptedArchive(archive, account, password)
    }

    @Test fun encryptedEnvelopeForAnotherAccountCannotBeExportedAsThisHousehold() {
        val source = completeSyntheticV2()
        source.getJSONObject("account").put("email", "another@example.invalid")
        // Same correct GCM material, deliberately conflicting inner identity.
        val encrypted = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            source.toString(), account, password)
        assertThrows(IllegalArgumentException::class.java) {
            writer().validateEncryptedArchive(encrypted, account, password)
        }
    }

    @Test fun authenticatedEnvelopeWithOmittedLedgerCannotBePublished() {
        val source = completeSyntheticV2().also { it.remove("ledgerEvents") }
        val archive = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            source.toString(), account, password)
        assertThrows(Exception::class.java) {
            writer().validateEncryptedArchive(archive, account, password)
        }
    }

    @Test fun authenticatedEnvelopeWithConflictingFinancialUnitsCannotBePublished() {
        val source = completeSyntheticV2()
        source.getJSONArray("holdings").put(
            JSONObject().put("id", "holding").put("ownerId", "owner")
                .put("name", "Synthetic security").put("category", "EQUITY")
                .put("subCategory", "").put("symbolOrFolio", "SYNTH")
                .put("quantityScaled", "100000000").put("currentPriceMinor", "10000")
                .put("averageCostPerUnitMinor", "10000")
                .put("lastPriceUpdated", "2026-10-20")
                .put("priceSource", "SYNTHETIC"))
        // A valid GCM tag with no ledger event still cannot prove 1 unit.
        val archive = LegacyBackupEnvelopeWriter.encryptAuthenticatedJson(
            source.toString(), account, password)
        assertThrows(Exception::class.java) {
            writer().validateEncryptedArchive(archive, account, password)
        }
    }

    @Test fun unknownProviderMtimeMustNeverPruneNewlyVerifiedEncryptedArchive() {
        fun entry(id: String, name: String, mtime: Long) =
            NativeSafEncryptedBackupWriter.Entry(id, name, mtime)
        val old = (1..7).map { i ->
            entry("old-$i", "GrowUrDhan-Native-20261020T00000${i}Z-00ab01fe.kosha",
                i.toLong())
        }
        val fresh = entry("just-published",
            "GrowUrDhan-Native-20261020T100000Z-c0ffee00.kosha", 0L)
        val entries = old + fresh + entry("unrelated", "family-document.pdf", 0L)
        val obsolete = NativeSafEncryptedBackupWriter.obsolete(
            entries, protectedId = fresh.id)
        assertEquals(3, obsolete.size)
        assertFalse(obsolete.any { it.id == fresh.id })
        assertTrue(obsolete.all { it.id.startsWith("old-") })
        assertEquals(3L, obsolete.maxOf { it.lastModified })
    }

    @Test fun lastFiveUsesTrustedBackupNameRatherThanForgedProviderMtime() {
        fun entry(id: Int, providerTime: Long) =
            NativeSafEncryptedBackupWriter.Entry(
                "id-$id", "GrowUrDhan-Native-20261020T00000${id}Z-00ab01fe.kosha",
                providerTime)
        val source = (1..7).map { i ->
            entry(i, if (i == 1) Long.MAX_VALUE else 0L)
        } + NativeSafEncryptedBackupWriter.Entry(
            "latest", "GrowUrDhan-Native-20261020T100000Z-c0ffee00.kosha", 0L)
        val toPrune = NativeSafEncryptedBackupWriter.obsolete(
            source, protectedId = "latest")
        assertEquals(setOf("id-1", "id-2", "id-3"),
            toPrune.map { it.id }.toSet())
    }

    @Test fun missingProtectedDocumentOrDuplicateProviderIdsRefuseAllDeletion() {
        fun entry(id: String) = NativeSafEncryptedBackupWriter.Entry(
            id, "GrowUrDhan-Native-20261020T100000Z-c0ffee00.kosha", 0L)
        assertThrows(IllegalArgumentException::class.java) {
            NativeSafEncryptedBackupWriter.obsolete(
                listOf(entry("old")), protectedId = "newly-verified")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSafEncryptedBackupWriter.obsolete(
                listOf(entry("duplicate"), entry("duplicate")),
                protectedId = "duplicate")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSafEncryptedBackupWriter.obsolete(
                listOf(entry("one"), entry("two")),
                protectedId = "one")
        }
    }

}
