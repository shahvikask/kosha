package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.CustodyAccount
import `in`.growurdhan.core.model.Holding
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.QuantityScaled
import java.util.Locale

/**
 * Validated, deliberately narrow CAS enrolment. Account labels are nicknames,
 * never PAN, customer numbers or credentials. All validation is repeated within
 * the repository's atomic transaction.
 */
object CustodyHoldingOnboarding {
    val registrarTypes = listOf("CAMS", "KFINTECH")
    private val isinPattern = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")
    private val folioPattern = Regex("^[A-Za-z0-9][A-Za-z0-9/._-]{0,63}$")
    const val MAX_ACCOUNTS_PER_OWNER = 100
    const val MAX_CAS_HOLDINGS_PER_OWNER = 2_000

    fun account(
        ownerId: OwnerId, type: String, label: String, id: String,
        existing: List<CustodyAccount>,
    ): CustodyAccount {
        val cleanedLabel = label.trim().replace(Regex("\\s+"), " ")
        require(type in registrarTypes) { "Select a supported CAS registrar." }
        require(cleanedLabel.length in 1..64 && cleanedLabel.none { it.isISOControl() }) {
            "Use an account nickname with 1–64 characters."
        }
        // Account label is intentionally only a nickname, never a raw folio/PAN.
        require(!Regex("^[A-Z]{5}[0-9]{4}[A-Z]$", RegexOption.IGNORE_CASE).matches(cleanedLabel)) {
            "Do not enter a PAN as an account label."
        }
        require(existing.count { it.ownerId == ownerId } < MAX_ACCOUNTS_PER_OWNER) {
            "Registrar account limit reached."
        }
        require(existing.none { it.ownerId == ownerId && it.accountType == type &&
            it.identifierMasked.equals(cleanedLabel, ignoreCase = true)
        }) { "A registrar account with that nickname already exists." }
        return CustodyAccount(id, ownerId, type, cleanedLabel)
    }

    fun holding(
        ownerId: OwnerId, account: CustodyAccount, name: String,
        isinInput: String, folioInput: String, id: HoldingId,
        existing: List<CasHoldingIdentity>,
    ): Pair<Holding, CasHoldingIdentity> {
        require(account.ownerId == ownerId) { "Registrar account belongs to a different family member." }
        require(account.accountType in registrarTypes) { "Only CAS registrar accounts can enrol CAS funds." }
        val cleanedName = name.trim().replace(Regex("\\s+"), " ")
        require(cleanedName.length in 1..200 && cleanedName.none { it.isISOControl() }) {
            "Provide a mutual fund name (1–200 characters)."
        }
        val isin = isinInput.trim().uppercase(Locale.ROOT)
        val folio = folioInput.trim()
        require(isinPattern.matches(isin)) { "An ISIN must contain 12 letters/digits." }
        require(folioPattern.matches(folio)) { "Enter a valid registrar folio (1–64 characters)." }
        require(existing.count { it.ownerId == ownerId } < MAX_CAS_HOLDINGS_PER_OWNER) {
            "CAS holding limit reached."
        }
        require(existing.none { it.ownerId == ownerId && it.isin == isin &&
            it.folio.equals(folio, ignoreCase = true)
        }) { "This ISIN and folio are already enrolled for the family owner." }
        // No implied trade and no invented historic purchase price.
        val holding = Holding(
            id, ownerId, account.id, cleanedName, "MUTUAL_FUND",
            QuantityScaled(0), MoneyScaled(0),
        )
        return holding to CasHoldingIdentity(
            id, ownerId, account.id, isin, folio, account.accountType,
        )
    }
}
