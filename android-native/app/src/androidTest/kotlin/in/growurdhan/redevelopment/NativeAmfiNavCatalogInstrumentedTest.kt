package `in`.growurdhan.redevelopment

import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class NativeAmfiNavCatalogInstrumentedTest {
    private val header = "Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;" +
        "Scheme Name;Net Asset Value;Date\n"
    private fun rows(vararg data: String): ByteArray =
        (header + data.joinToString("\n", postfix = "\n")).toByteArray(Charsets.UTF_8)

    private fun eodZip(exchange: String, day: String, csv: String,
        duplicateFile: Boolean = false): ByteArray {
        val name = "BhavCopy_${exchange}_CM_0_0_0_${day}_F_0000.csv"
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (duplicateFile) {
                zip.putNextEntry(ZipEntry("extra.csv"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun officialLink(exchange: String, day: String): String = when (exchange) {
        "NSE" -> "https://nsearchives.nseindia.com/content/cm/" +
            "BhavCopy_NSE_CM_0_0_0_${day}_F_0000.csv.zip"
        "BSE" -> "https://www.bseindia.com/download/BhavCopy/Equity/" +
            "BhavCopy_BSE_CM_0_0_0_${day}_F_0000.CSV"
        else -> error("Unexpected test exchange")
    }

    @Test fun nseAndBseMockHttpArchiveUseExactIsinCloseAndDate() {
        for (exchange in listOf("NSE", "BSE")) {
            val source = NativeExchangeEodCatalog.source(
                officialLink(exchange, "20261027"),
                NativeExchangeEodCatalog.Exchange.valueOf(exchange),
                LocalDate.parse("2026-10-28"))
            val csv = if (exchange == "NSE")
                "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,123.4567,2026-10-27,EQ\\n" +
                    "INE000000002,10.0000,2026-10-27,BE\\n"
            else
                "ISIN,ClsPric,TradDt,FinInstrmTp\\nINE000000001,123.4567,2026-10-27,STK\\n" +
                    "INE000000002,10.0000,2026-10-27,STK\\n"
            val actualCsv = csv.replace("\\n", "\n")
            val archive = if (exchange == "NSE") eodZip(exchange, "20261027", actualCsv)
                else actualCsv.toByteArray(Charsets.UTF_8)
            var requested: String? = null
            val parsed = NativeExchangeEodDownloader.fetch(source) { url ->
                requested = url.toString()
                archive
            }
            assertEquals(2, parsed.quotes.size)
            assertEquals("2026-10-27", parsed.date)
            assertEquals(1_234_567L, parsed.quotes.getValue("INE000000001").minor)
            assertEquals("2026-10-27", parsed.quotes.getValue("INE000000001").date)
            assertEquals(source.url.toString(), requested)
            assertFalse(requested!!.contains("PAN"))
            assertFalse(requested!!.contains("INE000000001"))
        }
    }

    @Test fun exchangeSourceRejectsUnofficialUrlsAndUnboundedDatesBeforeTransport() {
        val now = LocalDate.parse("2026-10-28")
        val exchange = NativeExchangeEodCatalog.Exchange.NSE
        for (url in listOf(
            "http://nsearchives.nseindia.com/download/BhavCopy_NSE_CM_0_0_0_20261027_F_0000.csv.zip",
            "https://nsearchives.nseindia.com.attacker.invalid/download/BhavCopy_NSE_CM_0_0_0_20261027_F_0000.csv.zip",
            officialLink("NSE", "20261027") + "?isin=INE000000001",
            officialLink("NSE", "20261029"),
            officialLink("NSE", "20260801"),
            officialLink("BSE", "20261027")
        )) assertThrows(Exception::class.java) {
            NativeExchangeEodCatalog.source(url, exchange, now)
        }
    }

    @Test fun exchangeMockHttpRejectsContradictoryCloseAndZipPayloads() {
        val source = NativeExchangeEodCatalog.source(
            officialLink("NSE", "20261027"),
            NativeExchangeEodCatalog.Exchange.NSE, LocalDate.parse("2026-10-28"))
        val bad = listOf(
            "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,12.12345,2026-10-27,EQ\\n",
            "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,-12.5,2026-10-27,EQ\\n",
            "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,12.5,2026-10-28,EQ\\n",
            "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,12,2026-10-27,EQ\\n" +
                "INE000000001,13,2026-10-27,EQ\\n",
            "ISIN,LastPric,TradDt,SctySrs\\nINE000000001,12,2026-10-27,EQ\\n",
            "ISIN,ClsPric,TradDt,SctySrs\\nINE000000001,12,2026-10-27,FUT\\n"
        )
        for (csv in bad) assertThrows(Exception::class.java) {
            NativeExchangeEodCatalog.parse(
                eodZip("NSE", "20261027", csv.replace("\\n", "\n")), source)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeExchangeEodCatalog.parse(
                eodZip("NSE", "20261027",
                    "ISIN,ClsPric\nINE000000001,12\n", true), source)
        }
    }

    @Test fun frozenExchangeFiltersPreventDerivativePricesFromBecomingEquity() {
        val nse = NativeExchangeEodCatalog.source(officialLink("NSE", "20261027"),
            NativeExchangeEodCatalog.Exchange.NSE, LocalDate.parse("2026-10-28"))
        val nseRows = "ISIN,ClsPric,TradDt,SctySrs\\n" +
            "INE000000001,50.0000,2026-10-27,FUT\\n" +
            "INE000000002,20.0000,2026-10-27,EQ\\n" +
            "INE000000003,30.0000,2026-10-27,GB\\n"
        val nseCatalog = NativeExchangeEodCatalog.parse(
            eodZip("NSE", "20261027", nseRows.replace("\\n", "\n")), nse)
        assertFalse(nseCatalog.quotes.containsKey("INE000000001"))
        assertEquals("EQUITY", nseCatalog.quotes.getValue("INE000000002").category)
        assertEquals("GOLD_SGB", nseCatalog.quotes.getValue("INE000000003").category)
        val bse = NativeExchangeEodCatalog.source(officialLink("BSE", "20261027"),
            NativeExchangeEodCatalog.Exchange.BSE, LocalDate.parse("2026-10-28"))
        val bseRows = "ISIN,ClsPric,TradDt,FinInstrmTp\\n" +
            "INE000000001,50.0000,2026-10-27,FUT\\n" +
            "INE000000002,20.0000,2026-10-27,STK\\n"
        val bseCatalog = NativeExchangeEodCatalog.parse(
            bseRows.replace("\\n", "\n").toByteArray(Charsets.UTF_8), bse)
        assertEquals(setOf("INE000000002"), bseCatalog.quotes.keys)
    }

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
