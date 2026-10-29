package `in`.growurdhan.redevelopment

import java.math.BigInteger
import java.time.LocalDate

/**
 * Owner-initiated family emergency index. On-device/in-memory only: neither
 * a printable plaintext file nor a cloud dossier is written by this component.
 *
 * The original emergency sheet warns that nominations must be confirmed by
 * custodians; a typed nominee is not a legal appointment. No user password,
 * full PAN, DP/BO identity, token, raw SQL, or PDF content is read or copied.
 */
internal object NativeEmergencyDossier {
    data class Member(val id: String, val name: String, val relation: String,
        val maskedPan: String?)
    data class Account(val custodian: String, val accountType: String,
        val maskedReference: String?, val owner: String, val nominee: String?)
    data class Holding(val name: String, val category: String,
        val owner: String, val custody: String, val unitsScaled: Long,
        val valueMinor: Long?, val valueWarning: String?)
    data class Sheet(val preparedOn: String, val members: List<Member>,
        val accounts: List<Account>, val holdings: List<Holding>,
        val unresolvedValues: Int)

    // Merely containing '*' is NOT masking: ABCDE1234F* exposes a full PAN.
    private val panMask = Regex("[A-Z]{5}\\*{4}[A-Z]")
    private val accountMask = Regex("\\*{4,}[A-Za-z0-9]{0,4}")
    private val rawPan = Regex("[A-Z]{5}[0-9]{4}[A-Z]")
    private val unmaskedLongNumber = Regex("[0-9]{8,}")

    private fun safeMaskedPan(value: String?): String? =
        value?.trim()?.takeIf { panMask.matches(it) }

    private fun safeMaskedAccount(value: String?): String? =
        value?.trim()?.takeIf { it.length <= 80 && accountMask.matches(it) }

    /** Strip identifiers accidentally pasted into human-readable labels. */
    private fun safeLabel(value: String): String {
        val clean = value.trim()
        if (clean.isEmpty() || clean.length > 128 ||
            clean.any(Char::isISOControl)) return "[Unverified label]"
        val panAnyCase = Regex("[A-Z]{5}[0-9]{4}[A-Z]", RegexOption.IGNORE_CASE)
        return unmaskedLongNumber.replace(
            panAnyCase.replace(clean, "[PAN REDACTED]"),
            "[ACCOUNT REDACTED]")
    }

    private fun safeNominee(value: String?): String? =
        value?.trim()?.takeIf {
            it.length in 1..80 && it.none(Char::isISOControl) &&
                !rawPan.containsMatchIn(it.uppercase()) &&
                !unmaskedLongNumber.containsMatchIn(it)
        }

    fun prepare(book: NativeBooksRepository.Snapshot,
        explicitlyApproved: Boolean, today: LocalDate = LocalDate.now(),
        selectedOwnerId: String? = null): Sheet {
        require(explicitlyApproved) {
            "Emergency family dossier requires current authenticated owner confirmation"
        }
        val activePeople = book.people.filter { it.active }
        if (selectedOwnerId != null) require(
            activePeople.any { it.id == selectedOwnerId }
        ) { "Unknown or inactive dossier owner" }
        val scopedPeople = if (selectedOwnerId == null) activePeople
            else activePeople.filter { it.id == selectedOwnerId }
        val ids = scopedPeople.map { it.id }.toSet()
        val byId = scopedPeople.associateBy { it.id }
        val scopedCustody = book.custody.filter { it.personId in ids }
        val custody = scopedCustody.associateBy { it.id }
        val scopedHoldings = book.holdings.filter { it.personId in ids && !it.archived }
        require(scopedHoldings.all { h ->
            h.accountId == null ||
                scopedCustody.any { a -> a.id == h.accountId && a.personId == h.personId }
        }) { "Dossier custody and owner identity conflict" }
        val members = scopedPeople.map {
            Member(it.id, safeLabel(it.name), safeLabel(it.relation),
                safeMaskedPan(it.maskedPan))
        }
        val accounts = scopedCustody.map {
            Account(safeLabel(it.custodian), it.type, safeMaskedAccount(it.maskedReference),
                byId[it.personId]?.let { p -> safeLabel(p.name) }
                    ?: "Unknown recorded owner", safeNominee(it.nominee))
        }
        var unresolved = 0
        val holdings = scopedHoldings.map { h ->
            // An unresolved, absent or future-dated quote never becomes a
            // fabricated rupee value in an emergency document.
            val observed = h.priceDate?.let {
                runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
            }
            val validPrice = observed != null && !observed.isAfter(today)
            val stalePrice = observed != null && !observed.isAfter(today) &&
                java.time.temporal.ChronoUnit.DAYS.between(observed, today) >= 45L
            val priced = validPrice && !h.needsReview &&
                h.marketPriceMinor >= 0L && h.quantityScaled >= 0L &&
                h.priceSource != null
            val value = if (!priced) null else runCatching {
                BigInteger.valueOf(h.quantityScaled)
                    .multiply(BigInteger.valueOf(h.marketPriceMinor))
                    .divide(BigInteger.valueOf(100_000_000L)).longValueExact()
            }.getOrNull()
            if (value == null) unresolved++
            Holding(safeLabel(h.name), h.category,
                byId[h.personId]?.let { safeLabel(it.name) }
                    ?: "Unknown recorded owner",
                h.accountId?.let { custody[it]?.custodian?.let(::safeLabel) }
                    ?: "Not recorded",
                h.quantityScaled, value,
                if (value == null) "Value unverified — confirm source and date"
                else if (stalePrice) "Recorded value is stale — verify dated source"
                else if (h.unknownCost) "Investment cost unknown" else null)
        }
        return Sheet(today.toString(), members, accounts, holdings, unresolved)
    }
}
