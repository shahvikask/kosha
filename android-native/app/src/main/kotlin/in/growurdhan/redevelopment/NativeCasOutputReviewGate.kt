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
        // Every demat row must retain the exact DP/client provenance emitted
        // by the frozen sidecar. PAN and ISIN alone are not a custody key.
        // The legacy Snapshot adapter has no account-provenance field, so
        // this gate may validate demat output but MUST NOT authorize import.
        if (issuer == "CDSL" || issuer == "NSDL") {
            require(accounts.length() == 1) {
                "Demat source needs exactly one account for manual review"
            }
            val account = accounts.getJSONObject(0)
            fun strictId(value: String): String {
                require(value.isNotBlank() && value.length <= 32 &&
                    value.matches(Regex("[A-Za-z0-9]{1,32}"))) {
                    "Missing or invalid demat account provenance"
                }
                return value
            }
            val dp = strictId(account.optString("dpId").trim())
            val client = strictId(account.optString("clientId").trim())
            val sourceHoldings = document.getJSONArray("holdings")
            for (i in 0 until sourceHoldings.length()) {
                val row = sourceHoldings.getJSONObject(i)
                require(row.optString("dpId").trim() == dp &&
                    row.optString("clientId").trim() == client) {
                    "CAS demat row has missing or mismatched account provenance"
                }
            }
        }
        val holdings = document.getJSONArray("holdings")
        require(holdings.length() in 1..10000)
        val counts = document.getJSONObject("parsedTotals")
        require(counts.getInt("holdingsCount") == holdings.length()) {
            "CAS position totals are inconsistent"
        }
        // The frozen sidecar reports both individual holdings and summary
        // totals. Independently recompute its subtotal before trusting a
        // source with the owner's holdings or investment values. Otherwise a
        // complete:true flag and plausible grand total could conceal a
        // truncated, duplicated or changed source position.
        fun finiteAmount(value: Any): BigDecimal {
            require(value is Number && value.toString().length <= 40) {
                "CAS source is not a bounded numeric amount"
            }
            val n = BigDecimal(value.toString())
            require(n.signum() >= 0 && n.precision() <= 18) {
                "CAS position has a negative or imprecise amount"
            }
            return n
        }
        var positionsSubtotal = BigDecimal.ZERO
        for (index in 0 until holdings.length()) {
            val position = holdings.getJSONObject(index)
            // Each actual source position must have real, bounded numeric
            // units/NAV/value. No null, string or guessed replacement value.
            finiteAmount(position.get("quantity"))
            finiteAmount(position.get("price"))
            positionsSubtotal = positionsSubtotal.add(
                finiteAmount(position.get("value")))
        }
        val parsed = finiteAmount(counts.get("holdingsValue"))
        require(positionsSubtotal.subtract(parsed).abs() <= BigDecimal("0.02")) {
            "CAS holding values differ from their parsed total"
        }
        val reported = finiteAmount(document.getJSONObject("pdfTotals")
            .get("grand"))
        val delta = BigDecimal(document.get("delta").toString())
        require(delta.precision() <= 18) { "Unbounded CAS reconciliation delta" }
        require(parsed.signum() >= 0 && reported.signum() >= 0 &&
            parsed.subtract(reported).subtract(delta).abs() <= BigDecimal("0.02") &&
            delta.abs() < BigDecimal.ONE) { "Frozen CAS reconciliation totals are invalid" }
        val warnings = document.getJSONArray("warnings").length()
        require(warnings <= 100) { "Unbounded source parser warnings" }
        return Verified(issuer, to.toString(), pan, holdings.length(), warnings)
    }
}
