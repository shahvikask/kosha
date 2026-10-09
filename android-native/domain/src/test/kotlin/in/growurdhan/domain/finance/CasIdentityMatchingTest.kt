package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The checked CAS rows are synthetic. No real identifiers or PDF fixtures. */
class CasIdentityMatchingTest {
    private val owner = OwnerId("self")
    private val holding = FinancialHolding(
        HoldingId("h1"), owner, "Synthetic Index", "MUTUAL_FUND", MoneyScaled(0),
    )
    private val identity = CasHoldingIdentity(
        holding.holdingId, owner, "account-1", "INF000000001", "123", "CAMS",
    )
    private fun row(isin: String? = identity.isin, folio: String? = identity.folio,
                    name: String = holding.name) = CasParsedHolding(
        name, "MUTUAL_FUND", "7.12345678", "100.0000",
        null, isin, folio,
    )
    private fun report(
        rows: List<CasParsedHolding> = listOf(row()), fileType: String = "CAMS",
    ) = CasParsedReport(
        "PYTHON_CASPARSER", fileType, true, "2026-10-08", emptyList(),
        "a".repeat(64), rows,
    )
    private fun resolve(
        report: CasParsedReport = report(),
        identities: List<CasHoldingIdentity> = listOf(identity),
        holdings: List<FinancialHolding> = listOf(holding),
    ) = CasImportContract.validate(
        report, owner, holdings, LocalDate.parse("2026-10-08"), identities,
    )

    @Test fun exactEnrolledIsinFolioAndRegistrarMapsOneHolding() {
        val result = resolve()
        assertEquals(holding.holdingId, result.lines.single().holdingId)
        assertEquals(712_345_678L, result.lines.single().quantityScaled)
        assertNull(result.lines.single().cumulativeInvested)
    }

    @Test fun noEnrolledMappingNeverFallsBackToMatchingName() {
        assertThrows(CasValidationException::class.java) {
            resolve(identities = emptyList())
        }
    }

    @Test fun wrongIsinOrFolioCanNeverBeGuessedFromName() {
        for (entry in listOf(row(isin = "INF000000002"), row(folio = "456"))) {
            assertThrows(CasValidationException::class.java) {
                resolve(report(rows = listOf(entry)))
            }
        }
    }

    @Test fun missingIsinOrFolioCannotImport() {
        assertThrows(CasValidationException::class.java) {
            resolve(report(rows = listOf(row(isin = null))))
        }
        assertThrows(CasValidationException::class.java) {
            resolve(report(rows = listOf(row(folio = null))))
        }
    }

    @Test fun mismatchedCustodyRegistrarIsRejectedEvenIfIsinAndFolioMatch() {
        assertThrows(CasValidationException::class.java) {
            resolve(report(fileType = "KFINTECH"))
        }
        assertThrows(CasValidationException::class.java) {
            resolve(report(fileType = "NSDL"))
        }
    }

    @Test fun wrongFamilyMemberCannotBorrowEnrolledIdentity() {
        assertThrows(CasValidationException::class.java) {
            resolve(identities = listOf(identity.copy(ownerId = OwnerId("spouse"))))
        }
    }

    @Test fun frozenCamsAssetCategoryEquityIsNotMistakenForFundIdentity() {
        // CASPARSER emits category=EQUITY for equity schemes, while the enrolled
        // holding correctly uses assetCategory=MUTUAL_FUND.
        val parsed = row().copy(category = "EQUITY")
        assertEquals(holding.holdingId,
            resolve(report(rows = listOf(parsed))).lines.single().holdingId)
    }

    @Test fun frozenCamsDebtCategoryIsAcceptedUnderMappedMutualFund() {
        val parsed = row().copy(category = "FIXED_INCOME")
        assertEquals(holding.holdingId,
            resolve(report(rows = listOf(parsed))).lines.single().holdingId)
    }

    @Test fun changedSchemeNameRequiresManualReEnrollment() {
        assertThrows(CasValidationException::class.java) {
            resolve(report(rows = listOf(row(name = "Different scheme"))))
        }
    }

    @Test fun twoMappingsMatchingOneCasRowAreRejectedAsAmbiguous() {
        assertThrows(CasValidationException::class.java) {
            resolve(identities = listOf(
                identity, identity.copy(holdingId = HoldingId("h2")),
            ), holdings = listOf(holding, holding.copy(holdingId = HoldingId("h2"))))
        }
    }

    @Test fun repeatedParcelsOfOneHoldingRemainSubjectToCheckedCoalescing() {
        val result = resolve(report(rows = listOf(row(), row())))
        assertEquals(2, result.lines.size)
        assertEquals(1_424_691_356L,
            SnapshotReconciler.coalesce(result.lines).single().quantityScaled)
    }
}
