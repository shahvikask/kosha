package `in`.growurdhan.model

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Explicitly labelled CSV snapshot format (not an assertion of CAS PDF parsing).
 * Real CSV bytes stay in memory for review; only SHA-256, authenticated position
 * facts, and the original filename may enter the encrypted books after approval.
 *
 * Exact ordered header:
 * owner_pan,statement_date,isin,folio,quantity_scaled,unit_price_minor,current_value_minor
 */
object NativeStatementCsv {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val header = listOf("owner_pan", "statement_date", "isin", "folio",
        "quantity_scaled", "unit_price_minor", "current_value_minor")
    data class Position(val ownerPan: String, val date: String, val isin: String,
        val folio: String, val quantityScaled: Long, val unitPriceMinor: Long,
        val currentValueMinor: Long?)
    data class Snapshot(val sha256: String, val bytes: Int,
        val ownerPan: String, val date: String, val positions: List<Position>)

    private fun integer(raw: String): Long {
        require(raw.matches(Regex("(0|[1-9][0-9]*)")) &&
            raw.length <= 19) { "Statement amount must be unsigned scaled integer" }
        return raw.toLongOrNull() ?: error("Statement amount is outside 64-bit range")
    }

    /** Strict RFC4180-like cell parsing. A malformed quote is a hard failure. */
    private fun cells(text: String): List<List<String>> {
        val output = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var endedQuote = false
        var atStart = true
        var i = 0
        fun emitField() {
            fields.add(value.toString())
            value.setLength(0)
            atStart = true
            endedQuote = false
        }
        fun emitRow() {
            emitField()
            output.add(fields)
            require(output.size <= 10002) { "Statement has too many rows" }
            fields = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        value.append('"'); i++
                    } else {
                        quoted = false
                        endedQuote = true
                    }
                } else value.append(ch)
            } else {
                when (ch) {
                    '"' -> {
                        require(atStart && !endedQuote) { "Invalid CSV quoting" }
                        quoted = true
                        atStart = false
                    }
                    ',' -> emitField()
                    '\n' -> emitRow()
                    '\r' -> {
                        require(i + 1 < text.length && text[i + 1] == '\n') {
                            "Invalid CSV row ending"
                        }
                        emitRow(); i++
                    }
                    else -> {
                        require(!endedQuote && ch != '\u0000') { "Invalid CSV value" }
                        value.append(ch)
                        atStart = false
                    }
                }
            }
            i++
        }
        require(!quoted) { "Unterminated CSV quote" }
        if (value.isNotEmpty() || fields.isNotEmpty() || endedQuote) emitRow()
        return output
    }

    fun parse(sourceBytes: ByteArray): Snapshot {
        require(sourceBytes.isNotEmpty() && sourceBytes.size <= MAX_BYTES) {
            "Empty or oversized statement file"
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(sourceBytes)).toString()
            .removePrefix("\uFEFF")
        val rows = cells(text)
        require(rows.size in 2..10001 && rows.first() == header) {
            "Unsupported statement CSV columns"
        }
        val positions = rows.drop(1).map { row ->
            require(row.size == header.size) { "Incomplete statement CSV row" }
            val pan = row[0].trim().uppercase(java.util.Locale.ROOT)
            require(pan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))) {
                "Unverified statement owner PAN"
            }
            val date = LocalDate.parse(row[1]).toString()
            require(!LocalDate.parse(date).isAfter(LocalDate.now())) {
                "Future-dated statement refused"
            }
            val isin = row[2].trim().uppercase(java.util.Locale.ROOT)
            val folio = row[3].trim()
            require(isin.isNotEmpty() || folio.isNotEmpty()) {
                "Statement needs ISIN or folio"
            }
            if (isin.isNotEmpty()) require(isin.matches(Regex("[A-Z0-9]{12}")))
            require(folio.length <= 120 && folio.none { it.isISOControl() })
            Position(pan, date, isin, folio, integer(row[4]), integer(row[5]),
                row[6].takeIf { it.isNotEmpty() }?.let(::integer))
        }
        require(positions.all { it.ownerPan == positions.first().ownerPan &&
            it.date == positions.first().date }) {
            "One statement file must belong to one PAN and statement date"
        }
        val fingerprints = positions.map { it.isin to it.folio }
        require(fingerprints.size == fingerprints.toSet().size) {
            "Repeated instrument identity in statement"
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(sourceBytes)
            .joinToString("") { "%02x".format(it) }
        return Snapshot(sha, sourceBytes.size, positions.first().ownerPan,
            positions.first().date, positions)
    }
}
