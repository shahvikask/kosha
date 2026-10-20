package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeStatementCsvTest {
    private val header =
        "owner_pan,statement_date,isin,folio,quantity_scaled,unit_price_minor,current_value_minor\n"
    private fun source(line: String) = (header + line + "\n").toByteArray(Charsets.UTF_8)

    @Test fun exactScaledIntegerSnapshotPreservesIsinAndDatedQuantity() {
        val statement = NativeStatementCsv.parse(source(
            "ABCDE1234F,2025-03-31,INE000000001,,150000000,12500,18750"))
        assertEquals("ABCDE1234F", statement.ownerPan)
        assertEquals("2025-03-31", statement.date)
        assertEquals(150000000L, statement.positions.single().quantityScaled)
        assertEquals(12500L, statement.positions.single().unitPriceMinor)
        assertEquals(18750L, statement.positions.single().currentValueMinor)
        assertTrue(statement.sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun quotedFolioAndCrlfParsingDoNotLoseExactBytes() {
        val bytes = (header.replace("\n", "\r\n") +
            "ABCDE1234F,2025-03-31,,\"folio,002\",1,0,\r\n")
            .toByteArray(Charsets.UTF_8)
        val statement = NativeStatementCsv.parse(bytes)
        assertEquals("folio,002", statement.positions.single().folio)
        assertEquals(1L, statement.positions.single().quantityScaled)
        assertNull(statement.positions.single().currentValueMinor)
    }

    @Test fun malformedIncompleteAmbiguousOrFutureFilesFailClosed() {
        for (invalid in listOf(
            "ABCDE1234F,2025-03-31,INE000000001,,12.5,12000,",
            "ABCDE1234F,2025-03-31,INE000000001,,12,1e4,",
            "ABCDE1234F,2025-03-31,,,,,",
            "ABCDE1234F,2099-03-31,INE000000001,,1,10,",
            "INVALID,2025-03-31,INE000000001,,1,10,",
            "ABCDE1234F,2025-03-31,\"bad,1,10,",
            "ABCDE1234F,2025-03-31,INE000000001,,1,10,"
                .plus("\nABCDE1234F,2025-03-31,INE000000001,,1,10,")
        )) {
            assertThrows(Exception::class.java) { NativeStatementCsv.parse(source(invalid)) }
        }
        assertThrows(Exception::class.java) {
            NativeStatementCsv.parse(byteArrayOf(0xC3.toByte(), 0x28))
        }
    }

    @Test fun aSingleFileMayNotContainMixedPanOrDates() {
        val mixed = header +
            "ABCDE1234F,2025-03-31,INE000000001,,1,2,\n" +
            "PQRST1234F,2025-03-31,INE000000002,,1,2,\n"
        assertThrows(IllegalArgumentException::class.java) {
            NativeStatementCsv.parse(mixed.toByteArray())
        }
    }
}
