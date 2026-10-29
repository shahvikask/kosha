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

    private fun safeNominee(value: String?): String? =
        value?.trim()?.takeIf {
            it.length in 1..80 && it.none(Char::isISOControl) &&
                !rawPan.containsMatchIn(it.uppercase()) &&
                !unmaskedLongNumber.containsMatchIn(it)
        }

    fun prepare(book: NativeBooksRepository.Snapshot,
        explicitlyApproved: Boolean, today: LocalDate = LocalDate.now()): Sheet {
        require(explicitlyApproved) {
            "Emergency family dossier requires current authenticated owner confirmation"
        }
        val byId = book.people.associateBy { it.id }
        val custody = book.custody.associateBy { it.id }
        val members = book.people.filter { it.active }.map {
            Member(it.id, it.name, it.relation, safeMaskedPan(it.maskedPan))
        }
        val accounts = book.custody.map {
            Account(it.custodian, it.type, safeMaskedAccount(it.maskedReference),
                byId[it.personId]?.name ?: "Unknown recorded owner", safeNominee(it.nominee))
        }
        var unresolved = 0
        val holdings = book.holdings.map { h ->
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
            Holding(h.name, h.category,
                byId[h.personId]?.name ?: "Unknown recorded owner",
                h.accountId?.let { custody[it]?.custodian } ?: "Not recorded",
                h.quantityScaled, value,
                if (value == null) "Value unverified — confirm source and date"
                else if (stalePrice) "Recorded value is stale — verify dated source"
                else if (h.unknownCost) "Investment cost unknown" else null)
        }
        return Sheet(today.toString(), members, accounts, holdings, unresolved)
    }
}
