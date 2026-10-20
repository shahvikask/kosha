package `in`.growurdhan.redevelopment

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeAmfiNavCatalogInstrumentedTest {
    private val header = "Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;" +
        "Scheme Name;Net Asset Value;Date\n"
    private fun rows(vararg data: String): ByteArray =
        (header + data.joinToString("\n", postfix = "\n")).toByteArray(Charsets.UTF_8)

    @Test fun officialAmfiStructureIsParsedToExactScaledPriceAndDatedIsin() {
        val parsed = NativeAmfiNavCatalog.parse(rows(
            "101101;INF000000001;INF000000002;Synthetic fund;12.3456;19-Oct-2026"),
            LocalDate.parse("2026-10-20"))
        assertEquals(2, parsed.quotes.size)
        assertEquals(123_456L, parsed.quotes.getValue("INF000000001").priceMinor)
        assertEquals("2026-10-19", parsed.quotes.getValue("INF000000002").date)
    }

    @Test fun conflictingOrFutureOrUnrepresentableNavFailsClosed() {
        for (bad in listOf(
            rows("101101;INF000000001;;Synthetic;12.3456;19-Oct-2026",
                "101102;INF000000001;;Synthetic;99.0000;19-Oct-2026"),
            rows("101101;INF000000001;;Synthetic;12.3456;21-Oct-2026"),
            rows("101101;INF000000001;;Synthetic;12.34567;19-Oct-2026"),
            rows("101101;INF000000001;;Synthetic;-12.00;19-Oct-2026")
        )) {
            assertThrows(Exception::class.java) {
                NativeAmfiNavCatalog.parse(bad, LocalDate.parse("2026-10-20"))
            }
        }
    }

    @Test fun invalidUtf8AndMissingOfficialIsinsCannotPretendToBeValidCatalog() {
        assertThrows(Exception::class.java) {
            NativeAmfiNavCatalog.parse(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(Exception::class.java) {
            NativeAmfiNavCatalog.parse(rows(
                "101101;not_an_isin;;Synthetic;12.0000;19-Oct-2026"))
        }
    }
}
