package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * M7 parser-to-enrolled-holding security vectors. Synthetic identifiers only.
 *
 * IMPORTANT: Production NativeCasPdfReader always supplies the enrolled identity
 * list (even when empty). The older name-only compatibility route must NEVER
 * be used to process an actual PDF document.
 */
class CasEnrolledIdentityVerificationTest {
    private val owner = OwnerId("synthetic-owner")
    private val otherOwner = OwnerId("synthetic-spouse")
    private val holding = FinancialHolding(
        HoldingId("mf-one"), owner, "Synthetic Equity Fund",
        "MUTUAL_FUND", MoneyScaled(0),
    )
    private val mapping = CasHoldingIdentity(
        holding.holdingId, owner, "registrar-a",
        "INF000000001", "FOL123", "CAMS",
    )
    private val today = LocalDate.parse("2026-10-08")

    private fun report(
        isin: String? = mapping.isin,
        folio: String? = mapping.folio,
        fileType: String = "CAMS",
        name: String = holding.name,
        category: String = holding.category,
    ) = CasParsedReport(
        engine = "PYTHON_CASPARSER",
        fileType = fileType,
        complete = true,
        statementDate = today.toString(),
        warnings = emptyList(),
        sourceSha256 = "a".repeat(64),
        holdings = listOf(
            CasParsedHolding(
                name, category, "12.50000000", "100.0000",
                null, isin, folio,
            ),
        ),
    )

    private fun strict(
        parsed: CasParsedReport = report(),
        registered: List<CasHoldingIdentity> = listOf(mapping),
        holdings: List<FinancialHolding> = listOf(holding),
    ) = CasImportContract.validate(
        report = parsed,
        owner = owner,
        holdings = holdings,
        today = today,
        enrolledIdentities = registered,
    )

    @Test fun exactOwnerRegistrarIsinAndFolioPrepareOnlyAReview() {
        val result = strict()
        assertEquals(owner, result.ownerId)
        assertEquals(holding.holdingId, result.lines.single().holdingId)
        assertEquals(1_250_000_000L, result.lines.single().quantityScaled)
        assertEquals(1_000_000L, result.lines.single().unitPrice.raw)
        assertNull(result.lines.single().cumulativeInvested)
        assertEquals("a".repeat(64), result.sourceSha256)
    }

    @Test fun emptyEnrollmentRegistryFailsEvenIfFundNameMatchesExactly() {
        assertThrows(CasValidationException::class.java) {
            strict(registered = emptyList())
        }
    }

    @Test fun anotherFamilyOwnersMappingCanNeverAuthorizeThisPdf() {
        assertThrows(CasValidationException::class.java) {
            strict(registered = listOf(mapping.copy(ownerId = otherOwner)))
        }
        assertThrows(CasValidationException::class.java) {
            strict(holdings = listOf(holding.copy(ownerId = otherOwner)))
        }
    }

    @Test fun wrongRegistrarCannotBorrowSameIsinAndFolio() {
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(fileType = "KFINTECH"))
        }
        assertThrows(CasValidationException::class.java) {
            strict(registered = listOf(mapping.copy(accountType = "KFINTECH")))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(fileType = "NSDL"))
        }
    }

    @Test fun modifiedIsinOrFolioAndMissingParsedIdentityFailClosed() {
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(isin = "INF000000002"))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(folio = "ANOTHER"))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(isin = null))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(folio = null))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(folio = "FOL 123"))
        }
    }

    @Test fun duplicateOrForgedMappingNeverChoosesArbitraryHolding() {
        val other = holding.copy(holdingId = HoldingId("mf-two"))
        val second = mapping.copy(holdingId = other.holdingId, accountId = "registrar-b")
        assertThrows(CasValidationException::class.java) {
            strict(registered = listOf(mapping, second), holdings = listOf(holding, other))
        }
        assertThrows(CasValidationException::class.java) {
            strict(registered = listOf(mapping.copy(holdingId = HoldingId("unknown"))),
                holdings = listOf(holding))
        }
    }

    @Test fun identityMatchWithDifferentFundNameOrAssetClassIsRejected() {
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(name = "Other Synthetic Scheme"))
        }
        assertThrows(CasValidationException::class.java) {
            strict(parsed = report(category = "EQUITY"))
        }
        assertThrows(CasValidationException::class.java) {
            strict(holdings = listOf(holding.copy(category = "EQUITY")))
        }
    }
}
