package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/**
 * Strict, side-effect-free boundary for output produced by the frozen on-device
 * cas_android.parse_b64 entry. It never accepts identities, costs or statement
 * fingerprints typed by the user as alternatives to parsed PDF evidence.
 *
 * Production imports MUST supply enrolled CAS identities. The optional legacy
 * fallback is only for old pure financial parity callers and must never be
 * used from the phone's PDF reader. No fuzzy name-only mapping is allowed
 * when the encrypted account/ISIN/folio registry is supplied.
 */
data class CasParsedHolding(
    val name: String,
    val category: String,
    val quantityDecimal: String,
    val unitPriceDecimal: String,
    val investedDecimal: String? = null,
    val isin: String? = null,
    val folio: String? = null,
)

data class CasParsedReport(
    val engine: String,
    val fileType: String,
    val complete: Boolean,
    val statementDate: String,
    val warnings: List<String>,
    val sourceSha256: String,
    val holdings: List<CasParsedHolding>,
)

data class CasValidatedPreview(
    val ownerId: OwnerId,
    val sourceSha256: String,
    val statementDate: LocalDate,
    val lines: List<StatementLine>,
)

class CasValidationException(message: String) : IllegalArgumentException(message)

object CasImportContract {
    const val MAX_PDF_BYTES = 30 * 1024 * 1024
    const val MAX_PARSED_HOLDINGS = 20_000
    private val fileTypes = setOf("CAMS", "KFINTECH", "NSDL", "CDSL", "NPS")
    private val hashPattern = Regex("^[a-f0-9]{64}$")
    private val isinPattern = Regex("^[A-Z]{2}[A-Z0-9]{10}$")
    private val folioPattern = Regex("^[A-Za-z0-9][A-Za-z0-9/._-]{0,63}$")

    /** Never translate a parsed PDF into a BUY, nor create an unknown holding. */
    fun validate(
        report: CasParsedReport,
        owner: OwnerId,
        holdings: List<FinancialHolding>,
        today: LocalDate,
        /** Non-null means fail closed and require exact owner/ISIN/folio/registrar binding. */
        enrolledIdentities: List<CasHoldingIdentity>? = null,
    ): CasValidatedPreview {
        if (report.engine != "PYTHON_CASPARSER" || report.fileType !in fileTypes ||
            !report.complete || report.warnings.isNotEmpty()
        ) throw CasValidationException("Parser result is incomplete, untrusted or requires review.")
        if (!hashPattern.matches(report.sourceSha256))
            throw CasValidationException("PDF fingerprint is invalid.")
        val date = try { LocalDate.parse(report.statementDate) } catch (_: Exception) {
            throw CasValidationException("The statement has no valid end date.")
        }
        if (date > today.plusDays(1))
            throw CasValidationException("The statement date is in the future.")
        if (report.holdings.isEmpty() || report.holdings.size > MAX_PARSED_HOLDINGS)
            throw CasValidationException("Empty or excessive parsed holdings; no import possible.")

        val existing = holdings.filter { it.ownerId == owner }
            .groupBy { identity(it.name, it.category) }
        val holdingsById = holdings.associateBy { it.holdingId }
        val lines = mutableListOf<StatementLine>()
        for (entry in report.holdings) {
            if (entry.name.isBlank() || entry.name.length > 300 ||
                entry.category.isBlank() || entry.category.length > 50
            ) throw CasValidationException("Parser returned invalid holding identity.")
            val matched = if (enrolledIdentities == null) {
                // Legacy non-PDF financial golden tests only. Never select this
                // fallback in the Android PDF-import boundary.
                existing[identity(entry.name, entry.category)].orEmpty()
            } else {
                if (report.fileType !in setOf("CAMS", "KFINTECH")) {
                    throw CasValidationException("An enrolled CAS registrar is required.")
                }
                val isin = entry.isin?.trim()?.uppercase(Locale.ROOT)
                val folio = entry.folio?.trim()
                if (isin == null || !isinPattern.matches(isin) ||
                    folio == null || !folioPattern.matches(folio)
                ) throw CasValidationException("Parsed CAS row has no trustworthy ISIN/folio.")
                enrolledIdentities.filter { mapping ->
                    mapping.ownerId == owner &&
                        mapping.accountType == report.fileType &&
                        mapping.isin == isin &&
                        mapping.folio == folio
                }.mapNotNull { mapping ->
                    holdingsById[mapping.holdingId]?.takeIf {
                        it.ownerId == owner && it.category == "MUTUAL_FUND"
                    }
                }
            }
            if (matched.size != 1) {
                throw CasValidationException(
                    "CAS holding is not uniquely enrolled for this owner, registrar, ISIN and folio."
                )
            }
            if (enrolledIdentities != null &&
                identity(matched.single().name, matched.single().category) !=
                identity(entry.name, entry.category)
            ) {
                // Name mismatch can indicate a changed fund scheme or malicious
                // account crossover. Explicit re-enrolment required, not guessing.
                throw CasValidationException("CAS fund identity differs from the enrolled holding.")
            }
            val units = parseDecimal(entry.quantityDecimal, 8, "quantity")
            val price = parseDecimal(entry.unitPriceDecimal, 4, "unit price")
            val invested = entry.investedDecimal?.let { parseDecimal(it, 4, "invested amount") }
            if (units.signum() < 0 || price.signum() < 0 || (invested != null && invested.signum() < 0))
                throw CasValidationException("Negative CAS holding amounts are not accepted.")
            val qtyScaled = try { units.movePointRight(8).longValueExact() } catch (_: Exception) {
                throw CasValidationException("Statement quantity exceeds device range.")
            }
            val priceScaled = try { price.movePointRight(4).longValueExact() } catch (_: Exception) {
                throw CasValidationException("Statement price exceeds device range.")
            }
            val investedScaled = invested?.let {
                try { MoneyScaled(it.movePointRight(4).longValueExact()) } catch (_: Exception) {
                    throw CasValidationException("Statement cost exceeds device range.")
                }
            }
            lines += StatementLine(
                holdingId = matched.single().holdingId,
                ownerId = owner,
                statementDate = date,
                quantityScaled = qtyScaled,
                unitPrice = MoneyScaled(priceScaled),
                cumulativeInvested = investedScaled,
            )
        }
        // Lines with repeated holdings are coalesced only in the existing, pure
        // SnapshotReconciler, which checks owner/date and arithmetic overflow.
        SnapshotReconciler.coalesce(lines)
        return CasValidatedPreview(owner, report.sourceSha256, date, lines.toList())
    }

    private fun parseDecimal(input: String, decimals: Int, field: String): BigDecimal {
        if (input.length > 64 || !Regex("^(?:0|[0-9]{1,17})(?:\\.[0-9]{1,16})?$").matches(input))
            throw CasValidationException("Invalid CAS $field.")
        return try {
            BigDecimal(input).setScale(decimals, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            throw CasValidationException("Unrepresentable CAS $field.")
        }
    }

    private fun identity(name: String, category: String): String =
        name.trim().replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT) + "\u0000" + category.trim().uppercase(Locale.ROOT)
}
