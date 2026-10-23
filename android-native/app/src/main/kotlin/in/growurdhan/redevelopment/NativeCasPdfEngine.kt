package `in`.growurdhan.redevelopment

import android.content.Context
import android.util.Base64
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import `in`.growurdhan.model.NativeStatementCsv
import `in`.growurdhan.parser.ParserBoundary
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/**
 * On-device Chaquopy bridge. The sole parser call is to the FROZEN
 * scripts/cas_android.py::parse_b64 -> scripts/cas_parse_server.py ->
 * src/casparser-main. No financial HTTP endpoint or synthetic PDF parser.
 *
 * Reject rather than guess absent PAN, ISIN/folio, incomplete parser totals,
 * mixed ownership or a source amount not representable at native integer scales.
 */
internal class NativeCasPdfEngine(private val context: Context) {
    data class Parsed(val snapshot: NativeStatementCsv.Snapshot,
        val issuer: String, val positionCount: Int, val warnings: Int)

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun scaled(text: String, decimals: Int): Long {
        require(text.isNotBlank() && text != "null") { "Missing parsed amount" }
        val value = BigDecimal(text)
        require(value.signum() >= 0 && value.precision() <= 16) {
            "Invalid or imprecise parser numeric value"
        }
        return value.movePointRight(decimals).toBigIntegerExact().longValueExact()
    }

    /** Must run off main thread; never log returned JSON, PDF, PAN or password. */
    @Synchronized
    fun parse(pdf: ByteArray, password: String, displayName: String,
        ownerConfirmedPan: String): Parsed {
        require(pdf.size in 32..(16 * 1024 * 1024)) { "PDF size not supported" }
        require(pdf.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) {
            "Not a PDF file"
        }
        require(displayName.isNotBlank() && displayName.length <= 240)
        // The frozen sidecar can infer investorPan from the PDF password
        // if the issuer omitted PAN. Never silently treat that guess as an
        // independently printed identity: require a SEPARATE owner-attested
        // full PAN, later matched exactly to the encrypted family record.
        val confirmed = ownerConfirmedPan.trim().uppercase(java.util.Locale.ROOT)
        require(confirmed.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))) {
            "Explicit owner PAN confirmation required"
        }
        val sha = digest(pdf)
        return ParserBoundary.withAdmittedPdf(displayName, pdf) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
            val encoded = Base64.encodeToString(pdf, Base64.NO_WRAP)
            val response = Python.getInstance().getModule("cas_android")
                .callAttr("parse_b64", encoded, password, displayName).toString()
            val document = JSONObject(response)
            // Verify the REAL frozen sidecar's full response contract
            // independently before interpreting any source holdings.
            val verified = NativeCasOutputReviewGate.verify(document, confirmed)
            val date = verified.date
            val pan = verified.pan
            val rows = document.getJSONArray("holdings")
            val positions = (0 until rows.length()).map { index ->
                val holding = rows.getJSONObject(index)
                val isin = holding.optString("isin", "").trim()
                    .uppercase(java.util.Locale.ROOT)
                val folio = holding.optString("folio", "").trim()
                require(isin.isNotEmpty() || folio.isNotEmpty()) {
                    "CAS row lacks exact ISIN or folio"
                }
                if (isin.isNotEmpty()) require(isin.matches(Regex("[A-Z0-9]{12}")))
                require(folio.length <= 120 && folio.none { it.isISOControl() })
                // Keep original parser values; never round a reported cost/price.
                val q = scaled(holding.get("quantity").toString(), 8)
                val unit = scaled(holding.get("price").toString(), 4)
                val value = scaled(holding.get("value").toString(), 4)
                val custody = if (verified.issuer in setOf("CDSL", "NSDL")) {
                    // The output gate has verified this row against an exact
                    // issuer account record. Never infer DP/client from PAN,
                    // ISIN, account ordering, filename or password.
                    NativeStatementCsv.DematAccount(verified.issuer,
                        holding.getString("dpId").trim(),
                        holding.getString("clientId").trim())
                } else null
                NativeStatementCsv.Position(pan, date, isin, folio, q, unit,
                    value, custody)
            }
            require(positions.map { Triple(it.dematAccount, it.isin, it.folio) }
                .distinct().size == positions.size) {
                "Ambiguous issuer position identity; manual mapping required"
            }
            Parsed(NativeStatementCsv.Snapshot(sha, pdf.size, pan, date, positions,
                verified.issuer),
                verified.issuer, positions.size, verified.warnings)
        }
    }
}
