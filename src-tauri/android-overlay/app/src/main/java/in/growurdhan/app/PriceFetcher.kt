package `in`.growurdhan.app

import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

internal object PriceFetcher {
    fun refresh(isinsCsv: String): Triple<JSONArray, String?, JSONArray> {
        val wanted = isinsCsv.split(",")
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.length >= 12 }
            .toSet()
        val catalog = JSONArray()
        val warnings = JSONArray()
        var asOf: String? = null
        try {
            val nse = fillExchange(wanted, catalog, "NSE")
            if (nse != null) asOf = nse
        } catch (e: Exception) {
            warnings.put("NSE close: ${e.message ?: "unavailable"}")
        }
        try {
            val bse = fillExchange(wanted, catalog, "BSE")
            if (bse != null && (asOf == null || bse > asOf!!)) asOf = bse
        } catch (e: Exception) {
            warnings.put("BSE close: ${e.message ?: "unavailable"}")
        }
        try {
            val amfi = fillAmfi(wanted, catalog)
            if (amfi != null && (asOf == null || amfi > asOf!!)) asOf = amfi
        } catch (e: Exception) {
            warnings.put("AMFI NAV: ${e.message ?: "unavailable"}")
        }
        return Triple(catalog, asOf, warnings)
    }

    private fun fillAmfi(wanted: Set<String>, catalog: JSONArray): String? {
        var body = httpGet("https://www.amfiindia.com/spages/NAVAll.txt")
        if (body.isNullOrBlank() || !body.contains(';')) {
            body = httpGet("https://portal.amfiindia.com/spages/NAVAll.txt")
        }
        if (body.isNullOrBlank()) throw IllegalStateException("AMFI NAV file could not be downloaded.")
        var asOf: String? = null
        val months = monthMap()
        for (raw in body.split('\n')) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (!line.contains(';')) continue
            val cols = line.split(';').map { it.trim() }
            if (cols.size < 6) continue
            if (cols[0].lowercase(Locale.US).startsWith("scheme")) continue
            val nav = cols[cols.size - 2].replace(",", "").toDoubleOrNull() ?: continue
            if (nav <= 0.0) continue
            val dateRaw = cols.last()
            val iso = parseAmfiDate(dateRaw, months) ?: parseIsoDate(dateRaw)
            if (iso != null && (asOf == null || iso > asOf!!)) asOf = iso
            val wide = cols.size >= 8
            val name = fundName(cols[3], if (wide) cols[4] else "", if (wide) cols[5] else "")
            val code = cols[0]
            val isins = listOf(cols[1], cols[2])
                .map { it.uppercase(Locale.US) }
                .filter { it.length >= 12 && it != "NA" && it != "N/A" }
            for (isin in isins) {
                if (wanted.isNotEmpty() && !wanted.contains(isin)) continue
                if (alreadyHas(catalog, isin)) continue
                val row = JSONObject()
                row.put("isin", isin)
                row.put("name", name)
                row.put("amfi", code)
                row.put("cat", "MUTUAL_FUND")
                row.put("px", nav)
                row.put("dt", iso ?: dateRaw)
                row.put("src", "AMFI NAV")
                catalog.put(row)
            }
        }
        return asOf
    }

    private fun fundName(name: String, plan: String, option: String): String {
        val extra = listOf(plan, option).filter { part ->
            val t = part.trim().uppercase(Locale.US)
            t.isNotEmpty() && t != "-" && t != "NA" && t != "N/A"
        }
        return if (extra.isEmpty()) name else "$name — ${extra.joinToString(" · ")}"
    }

    private val NSE_SERIES = setOf("EQ", "BE", "SM", "ST", "GB")

    private fun fillExchange(wanted: Set<String>, catalog: JSONArray, exchange: String): String? {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        var last = "not published for the last sessions"
        for (i in 0 until 6) {
            val yyyy = cal.get(Calendar.YEAR)
            val mm = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            val dd = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val ymd = "$yyyy$mm$dd"
            val url = if (exchange == "NSE") {
                "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_${ymd}_F_0000.csv.zip"
            } else {
                "https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_${ymd}_F_0000.CSV"
            }
            val referer = if (exchange == "NSE") "https://www.nseindia.com/all-reports" else "https://www.bseindia.com/"
            try {
                val bytes = httpGetBytes(url, referer) ?: run {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                val csv = if (exchange == "NSE") unzipFirst(bytes) else String(bytes, Charsets.UTF_8)
                if (csv.isNullOrBlank() || csv.startsWith("<")) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                val parsed = parseUdff(csv, exchange, wanted, catalog)
                if (!parsed.understood) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                if (parsed.added > 0) return parsed.asOf
                return null
            } catch (e: Exception) {
                last = e.message ?: last
            }
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        throw IllegalStateException(last)
    }

    private class Udff(val asOf: String?, val understood: Boolean, val added: Int)

    private fun parseUdff(csv: String, exchange: String, wanted: Set<String>, catalog: JSONArray): Udff {
        val lines = csv.split('\n')
        if (lines.isEmpty()) return Udff(null, false, 0)
        val header = splitCsv(lines[0]).map { it.trim().trim('"').uppercase(Locale.US) }
        fun idx(name: String) = header.indexOf(name)
        val isinIdx = idx("ISIN")
        val closeIdx = idx("CLSPRIC").let { if (it >= 0) it else idx("CLOSE") }
        if (isinIdx < 0 || closeIdx < 0) return Udff(null, false, 0)
        val symbolIdx = idx("TCKRSYMB").let { if (it >= 0) it else idx("SYMBOL") }
        val nameIdx = idx("FININSTRMNM")
        val seriesIdx = idx("SCTYSRS").let { if (it >= 0) it else idx("SERIES") }
        val typeIdx = idx("FININSTRMTP")
        val dateIdx = listOf(idx("TRADDT"), idx("BIZDT"), idx("TIMESTAMP")).firstOrNull { it >= 0 } ?: -1
        var asOf: String? = null
        var added = 0
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = splitCsv(line)
            if (cols.size <= maxOf(isinIdx, closeIdx)) continue
            val isin = cols[isinIdx].trim().trim('"').uppercase(Locale.US)
            if (isin.length < 12) continue
            if (wanted.isNotEmpty() && !wanted.contains(isin)) continue
            if (alreadyHas(catalog, isin)) continue
            val series = if (seriesIdx >= 0 && cols.size > seriesIdx) cols[seriesIdx].trim().trim('"').uppercase(Locale.US) else ""
            if (exchange == "NSE" && series.isNotEmpty() && series !in NSE_SERIES) continue
            if (exchange == "BSE" && typeIdx >= 0 && cols.size > typeIdx) {
                val kind = cols[typeIdx].trim().trim('"').uppercase(Locale.US)
                if (kind.isNotEmpty() && kind != "STK") continue
            }
            val px = cols[closeIdx].trim().trim('"').replace(",", "").toDoubleOrNull() ?: continue
            if (px <= 0.0) continue
            val symbol = if (symbolIdx >= 0 && cols.size > symbolIdx) cols[symbolIdx].trim().trim('"').uppercase(Locale.US) else ""
            val name = if (nameIdx >= 0 && cols.size > nameIdx) cols[nameIdx].trim().trim('"') else symbol
            val rawDate = if (dateIdx >= 0 && cols.size > dateIdx) cols[dateIdx].trim().trim('"') else ""
            val iso = parseNseTimestamp(rawDate) ?: parseIsoDate(rawDate)
            if (iso != null) asOf = iso
            val row = JSONObject()
            row.put("isin", isin)
            row.put("name", if (name.isBlank()) symbol else name)
            row.put("symbol", symbol)
            if (exchange == "NSE") row.put("nse", symbol) else row.put("bse", symbol)
            row.put("cat", if (series == "GB") "GOLD_SGB" else "EQUITY")
            row.put("px", px)
            row.put("dt", iso ?: rawDate)
            row.put("src", "$exchange close")
            catalog.put(row)
            added += 1
        }
        return Udff(if (added > 0) asOf else null, true, added)
    }

    private fun parseIsoDate(raw: String): String? {
        val t = raw.trim()
        return if (t.length >= 10 && t[4] == '-' && t[7] == '-') t.substring(0, 10) else null
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun unzipFirst(bytes: ByteArray): String? {
        if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return null
        ZipInputStream(bytes.inputStream()).use { zip ->
            val entry = zip.nextEntry ?: return null
            if (entry.isDirectory) return null
            return zip.bufferedReader().readText()
        }
    }

    private fun parseAmfiDate(raw: String, months: Map<String, String>): String? {
        val parts = raw.split("-")
        if (parts.size != 3) return null
        val dd = parts[0].padStart(2, '0')
        val mon = months[parts[1].uppercase(Locale.US)] ?: return null
        val yyyy = parts[2]
        if (yyyy.length != 4) return null
        return "$yyyy-$mon-$dd"
    }

    private fun parseNseTimestamp(raw: String): String? {
        val parts = raw.uppercase(Locale.US).split("-")
        if (parts.size < 3) return null
        val dd = parts[0].filter { it.isDigit() }.padStart(2, '0')
        if (dd.length != 2) return null
        val mon = monthMap()[parts[1].take(3)] ?: return null
        val yyyy = parts[2].filter { it.isDigit() }.take(4)
        if (yyyy.length != 4) return null
        return "$yyyy-$mon-$dd"
    }

    private fun monthMap() = mapOf(
        "JAN" to "01", "FEB" to "02", "MAR" to "03", "APR" to "04",
        "MAY" to "05", "JUN" to "06", "JUL" to "07", "AUG" to "08",
        "SEP" to "09", "OCT" to "10", "NOV" to "11", "DEC" to "12"
    )

    private fun alreadyHas(catalog: JSONArray, isin: String): Boolean {
        for (i in 0 until catalog.length()) {
            if (catalog.getJSONObject(i).optString("isin") == isin) return true
        }
        return false
    }

    private fun httpGet(url: String): String? {
        val bytes = httpGetBytes(url, "https://www.amfiindia.com/") ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun httpGetBytes(url: String, referer: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = 10000
        conn.readTimeout = 40000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Referer", referer)
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val head = bytes.take(15).toByteArray()
            val text = String(head, Charsets.ISO_8859_1).lowercase(Locale.US)
            if (text.startsWith("<!doctype") || text.startsWith("<html")) return null
            return bytes
        } finally {
            conn.disconnect()
        }
    }
}
