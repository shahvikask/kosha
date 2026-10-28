package `in`.growurdhan.redevelopment

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream

/** P26: strict public exchange close files, not real-time market quotations. */
internal object NativeExchangeEodCatalog {
    enum class Exchange { NSE, BSE }
    data class Quote(val isin: String, val date: String, val minor: Long, val category: String)
    data class Catalog(val exchange: Exchange, val date: String,
        val quotes: Map<String, Quote>)
    data class Source(val exchange: Exchange, val date: LocalDate, val url: URL)
    private val nseName = Regex("BhavCopy_NSE_CM_0_0_0_([0-9]{8})_F_0000\\.csv\\.zip")
    private val bseName = Regex("BhavCopy_BSE_CM_0_0_0_([0-9]{8})_F_0000\\.CSV")
    private val isin = Regex("[A-Z0-9]{12}")

    fun source(link: String, exchange: Exchange, now: LocalDate = LocalDate.now()): Source {
        val url = URL(link.trim())
        require(url.protocol == "https" && url.port == -1 &&
            url.userInfo == null && url.query == null && url.ref == null) {
            "Only plain HTTPS official market URLs"
        }
        val host = if (exchange == Exchange.NSE)
            "nsearchives.nseindia.com" else "www.bseindia.com"
        require(url.host.lowercase(Locale.ROOT) == host) {
            "Unsupported market host"
        }
        val parent = if (exchange == Exchange.NSE) "/content/cm/"
            else "/download/BhavCopy/Equity/"
        val leaf = url.path.substringAfterLast('/')
        require(url.path == parent + leaf) { "Unknown exchange archive path" }
        val match = requireNotNull(
            (if (exchange == Exchange.NSE) nseName else bseName).matchEntire(leaf)
        ) { "Unsupported official EOD filename" }
        val day = LocalDate.parse(match.groupValues[1], DateTimeFormatter.BASIC_ISO_DATE)
        require(!day.isAfter(now) && !day.isBefore(now.minusDays(30))) {
            "Future or older than 30 days source archive"
        }
        return Source(exchange, day, url)
    }

    fun parse(zipBytes: ByteArray, source: Source): Catalog {
        require(zipBytes.size in 1..(8 * 1024 * 1024))
        val expected = source.url.path.substringAfterLast('/').removeSuffix(".zip")
        val csvBytes = if (source.exchange == Exchange.BSE) {
            require(zipBytes.size <= 8 * 1024 * 1024)
            zipBytes // Frozen eod_prices.py: BSE is plain .CSV, not a ZIP.
        } else ZipInputStream(zipBytes.inputStream()).use { zip ->
            val item = requireNotNull(zip.nextEntry) { "Missing market CSV" }
            require(!item.isDirectory && item.name == expected) {
                "Unexpected market archive entry"
            }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = zip.read(buffer)
                if (n < 0) break
                require(out.size().toLong() + n <= 24L * 1024 * 1024)
                out.write(buffer, 0, n)
            }
            require(zip.nextEntry == null) { "Extra market archive entry" }
            out.toByteArray()
        }
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(csvBytes)).toString()
        val lines = decoded.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size in 2..150_000)
        fun cells(line: String): List<String> {
            val result = mutableListOf<String>()
            val part = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                when {
                    line[i] == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                        part.append('"'); i++
                    }
                    line[i] == '"' -> quoted = !quoted
                    line[i] == ',' && !quoted -> { result.add(part.toString()); part.clear() }
                    else -> part.append(line[i])
                }
                i++
            }
            require(!quoted) { "Bad market CSV quotation" }
            result.add(part.toString())
            return result
        }
        val hdr = cells(lines.first().removePrefix("\uFEFF"))
            .map { it.trim().uppercase(Locale.ROOT) }
        require(hdr.distinct().size == hdr.size)
        val idColumn = hdr.indexOf("ISIN")
        val priceColumn = hdr.indexOf("CLSPRIC")
        require(idColumn >= 0 && priceColumn >= 0) { "Unknown EOD CSV columns" }
        val dateColumn = hdr.indexOf("TRADDT")
        val businessDateColumn = hdr.indexOf("BIZDT")
        val seriesColumn = hdr.indexOf("SCTYSRS")
        val typeColumn = hdr.indexOf("FININSTRMTP")
        require((dateColumn >= 0 || businessDateColumn >= 0) &&
            (if (source.exchange == Exchange.NSE) seriesColumn >= 0 else typeColumn >= 0)) {
            "Official type and observation date columns required"
        }
        val quotes = linkedMapOf<String, Quote>()
        for (line in lines.drop(1)) {
            val row = cells(line.trimEnd('\r'))
            require(row.size == hdr.size) { "Truncated market row" }
            // Frozen eod_prices.py filters stock/accepted NSE series before price parsing.
            val series = if (seriesColumn >= 0)
                row[seriesColumn].trim().uppercase(Locale.ROOT) else ""
            if (source.exchange == Exchange.NSE &&
                series !in setOf("EQ", "BE", "SM", "ST", "GB")) continue
            if (source.exchange == Exchange.BSE &&
                row[typeColumn].trim().uppercase(Locale.ROOT) != "STK") continue
            val id = row[idColumn].trim().uppercase(Locale.ROOT)
            if (id.isEmpty()) continue
            require(isin.matches(id)) { "Invalid source ISIN" }
            val minor = BigDecimal(row[priceColumn].trim()).movePointRight(4)
                .toBigIntegerExact().longValueExact()
            require(minor > 0L) { "Zero, negative, or imprecise source close" }
            val recorded = when {
                dateColumn >= 0 && row[dateColumn].isNotBlank() -> row[dateColumn].trim()
                businessDateColumn >= 0 -> row[businessDateColumn].trim()
                else -> ""
            }
            require(recorded in setOf(source.date.toString(),
                source.date.format(DateTimeFormatter.BASIC_ISO_DATE))) {
                "Market row date conflicts with source archive date"
            }
            val category = if (source.exchange == Exchange.NSE && series == "GB")
                "GOLD_SGB" else "EQUITY"
            val q = Quote(id, source.date.toString(), minor, category)
            val prev = quotes.putIfAbsent(id, q)
            require(prev == null || prev == q) { "Contradictory ISIN closes" }
        }
        require(quotes.isNotEmpty()) { "No official close observations" }
        return Catalog(source.exchange, source.date.toString(), quotes)
    }
}
