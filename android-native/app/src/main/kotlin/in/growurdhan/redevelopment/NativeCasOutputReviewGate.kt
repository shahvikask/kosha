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
        // A consolidated CAS may contain several demat accounts. Admit
        // ONLY a complete list of exact issuer identifiers and preserve the
        // account key on each row; PAN+ISIN alone is never a custody identity.
        if (issuer == "CDSL" || issuer == "NSDL") {
            require(accounts.length() in 1..1000) {
                "Demat source has missing or unbounded custody accounts"
            }
            fun strictId(value: String): String {
                require(value.isNotBlank() && value.length <= 32 &&
                    value.matches(Regex("[A-Za-z0-9]{1,32}"))) {
                    "Missing or invalid demat account provenance"
                }
                return value
            }
            // JSON null is distinct from the literal string "null": a
            // genuine folio summary omits DP/client IDs in the sidecar.
            fun optionalId(obj: JSONObject, key: String): String =
                if (obj.isNull(key)) "" else obj.optString(key).trim()
            val accountKeys = mutableSetOf<Pair<String, String>>()
            var folioSummaryCount = 0
            for (i in 0 until accounts.length()) {
                val account = accounts.getJSONObject(i)
                val type = account.optString("type").trim()
                    .lowercase(Locale.ROOT)
                val dp = optionalId(account, "dpId")
                val client = optionalId(account, "clientId")
                if ("mutual fund" in type) {
                    // The frozen sidecar uses an aggregate MF-folio account,
                    // not a DP/client pair. More than one summary is ambiguous:
                    // no per-folio source-account key exists to distinguish it.
                    require(dp.isBlank() && client.isBlank() &&
                        ++folioSummaryCount == 1) {
                        "Mixed CAS folio summary has ambiguous account provenance"
                    }
                } else {
                    val key = strictId(dp) to strictId(client)
                    require(accountKeys.add(key)) {
                        "Duplicate source demat account provenance"
                    }
                }
            }
            val seen = mutableSetOf<Triple<String, String, String>>()
            val sourceHoldings = document.getJSONArray("holdings")
            for (i in 0 until sourceHoldings.length()) {
                val row = sourceHoldings.getJSONObject(i)
                val kind = row.optString("kind")
                val accountType = row.optString("accountType")
                    .lowercase(Locale.ROOT)
                val dp = optionalId(row, "dpId")
                val client = optionalId(row, "clientId")
                val isin = row.optString("isin").trim()
                val folio = row.optString("folio").trim()
                if ("mutual fund" in accountType) {
                    // A consolidated MF-folio position is provable only from
                    // an explicit frozen source account type, its row kind and
                    // exact folio. Never infer this from a missing DP/client.
                    require(folioSummaryCount == 1 && kind == "mutual_fund" &&
                        dp.isBlank() && client.isBlank() &&
                        folio.isNotBlank() && folio.length <= 120 &&
                        folio.none { it.isISOControl() } &&
                        (isin.isBlank() || isin.matches(Regex("[A-Z0-9]{12}"))) &&
                        seen.add(Triple("FOLIO", isin, folio))) {
                        "Mixed CAS fund-folio row lacks exact source provenance"
                    }
                } else {
                    val key = strictId(dp) to strictId(client)
                    require(isin.matches(Regex("[A-Z0-9]{12}")) &&
                        key in accountKeys &&
                        seen.add(Triple(key.first, key.second, isin))) {
                        "Demat row has ambiguous or missing account/ISIN provenance"
                    }
                }
            }
        } else {
            // Preserve the previous fail-closed non-demat issuer rule.
            // Only fully accounted CDSL/NSDL statements support more than
            // one issuer account; never broaden CAMS/KFINTECH/NPS implicitly.
            require(accounts.length() <= 1) {
                "Multiple non-demat accounts require separate owner review"
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
        val nonDematSourceKeys = mutableSetOf<Pair<String, String>>()
        for (index in 0 until holdings.length()) {
            val position = holdings.getJSONObject(index)
            // A preview is not permission to guess an issuer's identity. For
            // non-demat sources every row needs an exact ISIN or folio, and
            // distinct positions must not silently double-count the same key.
            // Demat account/ISIN combinations are checked separately above.
            if (issuer !in setOf("CDSL", "NSDL")) {
                val isin = position.optString("isin").trim()
                val folio = position.optString("folio").trim()
                require((isin.isNotEmpty() || folio.isNotEmpty()) &&
                    (isin.isEmpty() || isin.matches(Regex("[A-Z0-9]{12}"))) &&
                    folio.length <= 120 && folio.none { it.isISOControl() } &&
                    nonDematSourceKeys.add(isin to folio)) {
                    "Non-demat CAS position lacks unique exact ISIN/folio provenance"
                }
            }
            // Each actual source position must have real, bounded numeric
            // units/NAV/value. No null, string or guessed replacement value.
            val units = finiteAmount(position.get("quantity"))
            finiteAmount(position.get("price"))
            val value = finiteAmount(position.get("value"))
            // A zero-unit closing position must never silently claim value.
            // Preserve explicit zero rows for owner review; reject contradictory totals.
            require(units.signum() != 0 || value.signum() == 0) {
                "Zero-unit CAS position has nonzero value"
            }
            positionsSubtotal = positionsSubtotal.add(value)
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
