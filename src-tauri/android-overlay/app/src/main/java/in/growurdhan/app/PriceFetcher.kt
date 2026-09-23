package `in`.growurdhan.app

import java.io.BufferedReader
import java.io.InputStreamReader
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
        var amfiAsOf: String? = null
        var nseAsOf: String? = null
        try {
            amfiAsOf = fillAmfi(wanted, catalog)
        } catch (e: Exception) {
            warnings.put("AMFI NAV: ${e.message ?: "unavailable"}")
        }
        try {
            nseAsOf = fillNseBhav(wanted, catalog)
        } catch (e: Exception) {
            warnings.put("NSE close: ${e.message ?: "unavailable"}")
        }
        return Triple(catalog, nseAsOf ?: amfiAsOf, warnings)
    }

    private fun fillAmfi(wanted: Set<String>, catalog: JSONArray): String? {
        val body = httpGet("https://www.amfiindia.com/spages/NAVAll.txt")
            ?: throw IllegalStateException("AMFI NAV file could not be downloaded.")
        var asOf: String? = null
        val months = monthMap()
        for (raw in body.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || !line.contains(';')) continue
            val cols = line.split(';')
            if (cols.size < 6) continue
            val nav = cols[4].trim().toDoubleOrNull() ?: continue
            if (nav <= 0.0) continue
            val dateRaw = cols[5].trim()
            val iso = parseAmfiDate(dateRaw, months)
            if (iso != null) asOf = iso
            val name = cols[3].trim()
            val code = cols[0].trim()
            val isins = listOf(cols[1].trim(), cols[2].trim())
                .map { it.uppercase(Locale.US) }
                .filter { it.length >= 12 }
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

    private fun fillNseBhav(wanted: Set<String>, catalog: JSONArray): String? {
        val cal = Calendar.getInstance()
        for (i in 0 until 8) {
            val yyyy = cal.get(Calendar.YEAR)
            val mon = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)?.uppercase(Locale.US) ?: return null
            val dd = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val url = "https://archives.nseindia.com/content/historical/EQUITIES/$yyyy/$mon/cm${dd}${mon}${yyyy}bhav.csv.zip"
            try {
                val csv = httpGetZipFirst(url)
                if (csv == null) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                var asOf: String? = null
                val lines = csv.split('\n')
                if (lines.isEmpty()) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                val header = lines[0].split(',').map { it.trim().uppercase(Locale.US) }
                val isinIdx = header.indexOf("ISIN")
                val closeIdx = header.indexOf("CLOSE")
                val symbolIdx = header.indexOf("SYMBOL")
                val seriesIdx = header.indexOf("SERIES")
                val tsIdx = header.indexOf("TIMESTAMP")
                if (isinIdx < 0 || closeIdx < 0) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    continue
                }
                for (line in lines.drop(1)) {
                    if (line.isBlank()) continue
                    val cols = line.split(',')
                    if (cols.size <= maxOf(isinIdx, closeIdx)) continue
                    val isin = cols[isinIdx].trim().uppercase(Locale.US)
                    if (isin.length < 12) continue
                    if (wanted.isNotEmpty() && !wanted.contains(isin)) continue
                    if (alreadyHas(catalog, isin)) continue
                    val series = if (seriesIdx >= 0 && cols.size > seriesIdx) cols[seriesIdx].trim() else "EQ"
                    if (series.isNotEmpty() && series != "EQ" && series != "BE" && series != "SM") continue
                    val px = cols[closeIdx].trim().toDoubleOrNull() ?: continue
                    if (px <= 0.0) continue
                    val symbol = if (symbolIdx >= 0 && cols.size > symbolIdx) cols[symbolIdx].trim() else ""
                    val ts = if (tsIdx >= 0 && cols.size > tsIdx) cols[tsIdx].trim() else ""
                    val iso = parseNseTimestamp(ts) ?: "%04d-%02d-%02d".format(yyyy, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    asOf = iso
                    val row = JSONObject()
                    row.put("isin", isin)
                    row.put("name", symbol)
                    row.put("symbol", symbol)
                    row.put("nse", symbol)
                    row.put("cat", "EQUITY")
                    row.put("px", px)
                    row.put("dt", iso)
                    row.put("src", "NSE close")
                    catalog.put(row)
                }
                return asOf
            } catch (_: Exception) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        return null
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
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = 20000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 GrowUrDhan")
        conn.setRequestProperty("Accept", "*/*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetZipFirst(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.connectTimeout = 20000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 GrowUrDhan")
        conn.setRequestProperty("Accept", "*/*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            ZipInputStream(conn.inputStream).use { zip ->
                val entry = zip.nextEntry ?: return null
                if (entry.isDirectory) return null
                return InputStreamReader(zip).readText()
            }
        } finally {
            conn.disconnect()
        }
    }
}
