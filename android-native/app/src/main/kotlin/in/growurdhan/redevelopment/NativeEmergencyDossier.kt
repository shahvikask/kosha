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

    private fun safeMasked(value: String?): String? =
        value?.takeIf { it.length <= 80 && '*' in it &&
            it.none(Char::isISOControl) }

    fun prepare(book: NativeBooksRepository.Snapshot,
        explicitlyApproved: Boolean, today: LocalDate = LocalDate.now()): Sheet {
        require(explicitlyApproved) {
            "Emergency family dossier requires current authenticated owner confirmation"
        }
        val byId = book.people.associateBy { it.id }
        val custody = book.custody.associateBy { it.id }
        val members = book.people.filter { it.active }.map {
            Member(it.id, it.name, it.relation, safeMasked(it.maskedPan))
        }
        val accounts = book.custody.map {
            Account(it.custodian, it.type, safeMasked(it.maskedReference),
                byId[it.personId]?.name ?: "Unknown recorded owner", it.nominee)
        }
        var unresolved = 0
        val holdings = book.holdings.map { h ->
            // An unresolved, absent or future-dated quote never becomes a
            // fabricated rupee value in an emergency document.
            val validPrice = h.priceDate?.let { runCatching {
                !LocalDate.parse(it.take(10)).isAfter(today)
            }.getOrDefault(false) } == true
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
                else if (h.unknownCost) "Investment cost unknown" else null)
        }
        return Sheet(today.toString(), members, accounts, holdings, unresolved)
    }
}
