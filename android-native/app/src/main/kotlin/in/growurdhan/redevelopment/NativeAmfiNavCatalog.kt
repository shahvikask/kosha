package `in`.growurdhan.redevelopment

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.time.Instant

/**
 * AMFI public NAVAll.txt adapter, directly referencing the frozen
 * scripts/eod_prices.py source URI/column format. No household identifiers
 * are included in the request; the entire official catalog is downloaded.
 * NOT NSE/BSE support and NOT official pricing acceptance until device-tested.
 */
internal object NativeAmfiNavCatalog {
    private val fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    private val isin = Regex("[A-Z0-9]{12}")
    data class Quote(val isin: String, val date: String, val priceMinor: Long)
    data class Catalog(val quotes: Map<String, Quote>, val lineCount: Int)
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val OFFICIAL_URL = "https://www.amfiindia.com/spages/NAVAll.txt"

    fun parse(bytes: ByteArray, now: LocalDate = LocalDate.now()): Catalog {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        val quotes = linkedMapOf<String, Quote>()
        val lines = text.lineSequence().toList()
        require(lines.size in 2..200_000)
        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("Scheme Code;")) continue
            val cols = line.trimEnd('\r').split(';')
            // NAVAll includes scheme family headings and comments.
            if (cols.size != 6 || !cols[0].trim().matches(Regex("[0-9]{3,12}")))
                continue
            val nav = cols[4].trim()
            if (nav == "N.A." || nav.isBlank()) continue
            val minor = java.math.BigDecimal(nav).movePointRight(4)
                .toBigIntegerExact().longValueExact()
            require(minor > 0L)
            val date = LocalDate.parse(cols[5].trim(), fmt)
            require(!date.isAfter(now)) { "Future official NAV date" }
            for (field in listOf(cols[1], cols[2])) {
                val code = field.trim().uppercase(Locale.ROOT)
                if (!isin.matches(code)) continue
                val quote = Quote(code, date.toString(), minor)
                val prior = quotes.putIfAbsent(code, quote)
                require(prior == null || prior == quote) {
                    "Conflicting official NAV observations for identical ISIN"
                }
            }
        }
        require(quotes.isNotEmpty()) { "No valid official NAV/ISIN rows" }
        return Catalog(quotes, lines.size)
    }

    fun download(): ByteArray {
        val url = URL(OFFICIAL_URL)
        require(url.protocol == "https" && url.host == "www.amfiindia.com")
        val connection = url.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.useCaches = false
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "AMFI download unavailable; last encrypted price unchanged"
            }
            require(connection.contentLengthLong <= MAX_BYTES) {
                "Oversized official NAV file"
            }
            return connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_BYTES)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}

/** Persist official quotes only for matching encrypted MF ISINs. */
internal class NativeAmfiNavRepository(private val accounts: NativeAccountRepository) {
    data class Receipt(val matched: Int, val displayedPriceChanged: Int,
        val catalogRows: Int)

    fun refresh(email: String): Receipt {
        // No PAN, owner names, held ISINs, credentials or vault bytes sent.
        val catalog = NativeAmfiNavCatalog.parse(NativeAmfiNavCatalog.download())
        return applyVerifiedCatalog(email, catalog)
    }

    /** Reused by synthetic integration tests; never accepts arbitrary price provenance. */
    internal fun applyVerifiedCatalog(email: String, catalog: NativeAmfiNavCatalog.Catalog): Receipt {
        require(catalog.quotes.isNotEmpty())
        return accounts.withUnlockedVault(email) { db: SQLiteDatabase ->
            var matched = 0
            var updated = 0
            val observedInstruments = HashSet<String>()
            val recordedAt = Instant.now().toString()
            db.rawQuery("SELECT h.holding_id,i.instrument_id,i.isin " +
                "FROM holding h JOIN instrument i ON h.instrument_id=i.instrument_id " +
                "WHERE i.category='MUTUAL_FUND' AND i.isin IS NOT NULL",
                null).use { cur ->
                while (cur.moveToNext()) {
                    val source = catalog.quotes[cur.getString(2).uppercase(Locale.ROOT)]
                        ?: continue
                    matched++
                    val instrument = cur.getString(1)
                    if (NativeDatedPriceRecorder.record(db, cur.getString(0),
                            instrument, source.date, source.priceMinor,
                            "AMFI_OFFICIAL", recordedAt,
                            persistObservation = observedInstruments.add(instrument))) updated++
                }
            }
            Receipt(matched, updated, catalog.quotes.size)
        }
    }
}
