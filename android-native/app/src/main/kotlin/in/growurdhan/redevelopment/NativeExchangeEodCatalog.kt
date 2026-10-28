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
    data class Quote(val isin: String, val date: String, val minor: Long)
    data class Catalog(val exchange: Exchange, val date: String,
        val quotes: Map<String, Quote>)
    data class Source(val exchange: Exchange, val date: LocalDate, val url: URL)
    private val name = Regex("BhavCopy_(NSE|BSE)_CM_0_0_0_([0-9]{8})_F_0000\\.csv\\.zip")
    private val isin = Regex("[A-Z0-9]{12}")

    fun source(link: String, exchange: Exchange, now: LocalDate = LocalDate.now()): Source {
        val url = URL(link.trim())
        require(url.protocol == "https" && url.port == -1 &&
            url.userInfo == null && url.query == null && url.ref == null) {
            "Only plain HTTPS official market URLs"
        }
        val hosts = if (exchange == Exchange.NSE)
            setOf("nsearchives.nseindia.com", "archives.nseindia.com")
        else setOf("www.bseindia.com", "bseindia.com")
        require(url.host.lowercase(Locale.ROOT) in hosts) { "Unsupported market host" }
        val match = requireNotNull(name.matchEntire(url.path.substringAfterLast('/'))) {
            "Unsupported EOD archive filename"
        }
        require(match.groupValues[1] == exchange.name)
        val day = LocalDate.parse(match.groupValues[2], DateTimeFormatter.BASIC_ISO_DATE)
        require(!day.isAfter(now) && !day.isBefore(now.minusDays(30))) {
            "Future or older than 30 days source archive"
        }
        return Source(exchange, day, url)
    }

    fun parse(zipBytes: ByteArray, source: Source): Catalog {
        require(zipBytes.size in 1..(8 * 1024 * 1024))
        val expected = source.url.path.substringAfterLast('/').removeSuffix(".zip")
        val csvBytes = ZipInputStream(zipBytes.inputStream()).use { zip ->
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
        val quotes = linkedMapOf<String, Quote>()
        for (line in lines.drop(1)) {
            val row = cells(line.trimEnd('\r'))
            require(row.size == hdr.size) { "Truncated market row" }
            val id = row[idColumn].trim().uppercase(Locale.ROOT)
            if (id.isEmpty()) continue
            require(isin.matches(id)) { "Invalid source ISIN" }
            val minor = BigDecimal(row[priceColumn].trim()).movePointRight(4)
                .toBigIntegerExact().longValueExact()
            require(minor > 0L) { "Zero, negative, or imprecise source close" }
            if (dateColumn >= 0) require(row[dateColumn].trim() in setOf(
                source.date.toString(),
                source.date.format(DateTimeFormatter.BASIC_ISO_DATE))) {
                "Market row date conflicts with source archive date"
            }
            val q = Quote(id, source.date.toString(), minor)
            val prev = quotes.putIfAbsent(id, q)
            require(prev == null || prev == q) { "Contradictory ISIN closes" }
        }
        require(quotes.isNotEmpty()) { "No official close observations" }
        return Catalog(source.exchange, source.date.toString(), quotes)
    }
}
