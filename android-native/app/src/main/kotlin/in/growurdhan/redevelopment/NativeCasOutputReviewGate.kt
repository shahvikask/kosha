package `in`.growurdhan.redevelopment

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.json.JSONObject

/**
 * Pure, fail-closed consumer contract for FROZEN scripts/cas_parse_server.py.
 * Its one-device response is not a statement of independently verified owner
 * identity. Multiple demat accounts or records omitted by the parser require
 * separate per-account manual matching and cannot be mass-imported today.
 */
internal object NativeCasOutputReviewGate {
    data class Verified(val issuer: String, val date: String,
        val pan: String, val count: Int, val warnings: Int)

    fun verify(document: JSONObject, ownerConfirmedPan: String): Verified {
        require(document.optBoolean("ok", false) &&
            document.optString("engine") == "PYTHON_CASPARSER" &&
            document.optBoolean("complete", false)) {
            "Frozen CAS parser did not produce a complete report"
        }
        val issuer = document.optString("fileType")
        require(issuer in setOf("CAMS", "KFINTECH", "NSDL", "CDSL", "NPS")) {
            "Unsupported CAS registrar"
        }
        val pan = document.optString("investorPan").trim()
            .uppercase(Locale.ROOT)
        val affirmed = ownerConfirmedPan.trim().uppercase(Locale.ROOT)
        require(pan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]")) &&
            affirmed == pan) { "CAS PAN does not match separately confirmed owner" }
        val to = LocalDate.parse(document.getString("periodTo"))
        val from = LocalDate.parse(document.getString("periodFrom"))
        require(!from.isAfter(to) && !to.isAfter(LocalDate.now())) {
            "Invalid CAS statement period"
        }
        require(document.getInt("skippedNilHoldings") == 0) {
            "Parser omitted closed or nil CAS holdings"
        }
        val accounts = document.getJSONArray("accounts")
        require(accounts.length() <= 1) {
            "Multiple demat accounts require distinct verified account owners"
        }
        val holdings = document.getJSONArray("holdings")
        require(holdings.length() in 1..10000)
        val counts = document.getJSONObject("parsedTotals")
        require(counts.getInt("holdingsCount") == holdings.length()) {
            "CAS position totals are inconsistent"
        }
        val parsed = BigDecimal(counts.get("holdingsValue").toString())
        val reported = BigDecimal(document.getJSONObject("pdfTotals")
            .get("grand").toString())
        val delta = BigDecimal(document.get("delta").toString())
        require(parsed.signum() >= 0 && reported.signum() >= 0 &&
            parsed.subtract(reported).subtract(delta).abs() <= BigDecimal("0.02") &&
            delta.abs() < BigDecimal.ONE) { "Frozen CAS reconciliation totals are invalid" }
        val warnings = document.getJSONArray("warnings").length()
        require(warnings <= 100) { "Unbounded source parser warnings" }
        return Verified(issuer, to.toString(), pan, holdings.length(), warnings)
    }
}
