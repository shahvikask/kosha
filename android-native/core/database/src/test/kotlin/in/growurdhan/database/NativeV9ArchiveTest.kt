package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

/** Synthetic snapshots only. Never embed real financial identities in CI. */
class NativeV9ArchiveTest {
    private val password = "Synthetic separate backup secret 2026!".toCharArray()
    private val email = "synthetic.owner@example.invalid"

    private fun snapshot(): NativeV9Archive.Snapshot {
        val objects = SchemaV9Catalog.tables.map {
            NativeV9Archive.Schema("table", it.name, it.name,
                "CREATE TABLE " + it.name + " (synthetic INTEGER)")
        } + NativeV9Archive.Schema("trigger", "tg_ledger_event__no_delete",
            "ledger_event", "CREATE TRIGGER tg_ledger_event__no_delete")
        val tables = SchemaV9Catalog.tables.map { t ->
            val columns = t.columns.mapIndexed { i, name ->
                NativeV9Archive.Column(name, when (i % 3) {
                    0 -> "INTEGER"
                    1 -> "TEXT"
                    else -> "BLOB"
                }, false, 0, null)
            }
            val cells = t.columns.mapIndexed { i, _ -> when (i % 5) {
                0 -> NativeV9Archive.Cell.integer(Long.MIN_VALUE + 75)
                1 -> NativeV9Archive.Cell.text("synthetic ₹ parent/child/folio •")
                2 -> NativeV9Archive.Cell.blob(byteArrayOf(0, 1, -1, 127))
                3 -> NativeV9Archive.Cell.nullValue()
                else -> NativeV9Archive.Cell.real(-0.0)
            } }
            NativeV9Archive.Table(t.name, columns, listOf(cells))
        }
        return NativeV9Archive.Snapshot(objects, tables)
    }

    @Test fun everyNativeTableColumnAndSqliteStorageClassRoundTripsInMemory() {
        val original = snapshot()
        val bytes = NativeV9Archive.encodeInMemory(original)
        val recovered = NativeV9Archive.decodeInMemory(bytes)
        assertEquals(original, recovered)
        assertArrayEquals(bytes, NativeV9Archive.encodeInMemory(recovered))
        assertEquals(SchemaV9Catalog.tables.size, recovered.tables.size)
        assertEquals(Long.MIN_VALUE + 75,
            recovered.tables.first().rows.first().first().boundValue())
        assertEquals((-0.0).toRawBits(), (recovered.tables.first { it.columns.size > 4 }.rows.first()[4]
            .boundValue() as Double).toRawBits())
        assertArrayEquals(byteArrayOf(0, 1, -1, 127),
            recovered.tables.first().rows.first()[2].boundValue() as ByteArray)
    }

    @Test fun encryptedNativeEnvelopeDecryptsAndPreservesExactTypedSnapshot() {
        val original = snapshot()
        val a = NativeV9Archive.encrypt(original, email, password)
        val b = NativeV9Archive.encrypt(original, email, password)
        assertEquals("GUDNAT01", String(a.copyOfRange(0, 8), Charsets.US_ASCII))
        assertFalse(a.contentEquals(b))
        assertEquals(original, NativeV9Archive.decrypt(a, email, password))
        assertEquals(original, NativeV9Archive.decrypt(b,
            " SYNTHETIC.OWNER@EXAMPLE.INVALID ", password))
    }

    @Test fun tamperingWrongIdentityPasswordAndEnvelopeDowngradeFailClosed() {
        val archive = NativeV9Archive.encrypt(snapshot(), email, password)
        assertThrows(Exception::class.java) {
            NativeV9Archive.decrypt(archive, "other@example.invalid", password)
        }
        assertThrows(Exception::class.java) {
            NativeV9Archive.decrypt(archive, email, "incorrect backup password".toCharArray())
        }
        for (at in listOf(0, 8, 12, 28, 40, archive.lastIndex)) {
            val altered = archive.copyOf()
            altered[at] = (altered[at].toInt() xor 0x80).toByte()
            assertThrows(Exception::class.java) {
                NativeV9Archive.decrypt(altered, email, password)
            }
        }
        assertThrows(Exception::class.java) {
            NativeV9Archive.decrypt(archive.copyOf(archive.size - 1), email, password)
        }
        assertThrows(Exception::class.java) {
            NativeV9Archive.decrypt(archive + 0.toByte(), email, password)
        }
    }

    @Test fun malformedTypedRowsSchemaAndTrailingBytesCannotBeRecovered() {
        val valid = snapshot()
        val raw = NativeV9Archive.encodeInMemory(valid)
        assertThrows(Exception::class.java) {
            NativeV9Archive.decodeInMemory(raw + 1.toByte())
        }
        assertThrows(Exception::class.java) {
            NativeV9Archive.decodeInMemory(raw.copyOf(raw.size - 1))
        }
        val duplicate = valid.copy(tables = valid.tables +
            valid.tables.first())
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9Archive.encodeInMemory(duplicate)
        }
        val lostColumn = valid.copy(tables = valid.tables.mapIndexed { i, t ->
            if (i == 0) t.copy(rows = listOf(t.rows.first().dropLast(1))) else t
        })
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9Archive.encodeInMemory(lostColumn)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeV9Archive.encodeInMemory(valid.copy(tables =
                valid.tables.dropLast(1)))
        }
        val malformed = raw.copyOf()
        malformed[8] = 1 // unsupported version
        assertThrows(Exception::class.java) {
            NativeV9Archive.decodeInMemory(malformed)
        }
    }

    @Test fun unknownNativeMetadataIsPreservedInArchiveNotDiscarded() {
        val original = snapshot()
        val extra = NativeV9Archive.Table("new_historical_extension",
            listOf(NativeV9Archive.Column("raw_value", "BLOB", false, 0, null)),
            listOf(listOf(NativeV9Archive.Cell.blob(byteArrayOf(0, -3, 9)))))
        val changed = original.copy(tables = original.tables + extra,
            objects = original.objects + NativeV9Archive.Schema("table",
                "new_historical_extension", "new_historical_extension",
                "CREATE TABLE new_historical_extension (raw_value BLOB)"))
        val recovered = NativeV9Archive.decodeInMemory(
            NativeV9Archive.encodeInMemory(changed))
        assertEquals(changed, recovered)
        assertEquals(1, recovered.tables.single {
            it.name == "new_historical_extension" }.rows.size)
    }
}
