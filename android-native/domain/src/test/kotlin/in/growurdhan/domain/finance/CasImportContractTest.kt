package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic parser outputs only; no live CAS PDF or PAN is stored in fixtures. */
class CasImportContractTest {
    private val owner = OwnerId("owner")
    private val date = LocalDate.parse("2026-10-08")
    private val fund = FinancialHolding(
        HoldingId("h1"), owner, "Synthetic Index Fund", "MUTUAL_FUND", MoneyScaled(100_000),
    )
    private fun row(
        name: String = fund.name, category: String = fund.category,
        qty: String = "12.34567891", price: String = "123.4567",
        cost: String? = null,
    ) = CasParsedHolding(name, category, qty, price, cost)
    private fun report(
        rows: List<CasParsedHolding> = listOf(row()),
        engine: String = "PYTHON_CASPARSER", fileType: String = "CAMS",
        complete: Boolean = true, warnings: List<String> = emptyList(),
        sha: String = "a".repeat(64), end: String = "2026-10-08",
    ) = CasParsedReport(engine, fileType, complete, end, warnings, sha, rows)
    private fun validate(r: CasParsedReport, book: List<FinancialHolding> = listOf(fund)) =
        CasImportContract.validate(r, owner, book, date)

    @Test fun parsedPdfContractMapsKnownHoldingWithoutAddingNewTrade() {
        val selected = validate(report())
        assertEquals(HoldingId("h1"), selected.lines.single().holdingId)
        assertEquals(1_234_567_891L, selected.lines.single().quantityScaled)
        assertEquals(1_234_567L, selected.lines.single().unitPrice.raw)
        assertNull(selected.lines.single().cumulativeInvested)
        assertEquals("a".repeat(64), selected.sourceSha256)
    }

    @Test fun printedInvestedCostIsPreservedButMissingCostIsUnknown() {
        assertEquals(MoneyScaled(9_876_543L),
            validate(report(listOf(row(cost = "987.6543")))).lines.single().cumulativeInvested)
        assertNull(validate(report()).lines.single().cumulativeInvested)
    }

    @Test fun incompleteAndWarningBearingParserOutputIsRejected() {
        assertThrows(CasValidationException::class.java) { validate(report(complete = false)) }
        assertThrows(CasValidationException::class.java) { validate(report(warnings = listOf("drift"))) }
    }

    @Test fun parserIdentityMustBeFrozenEngineAndKnownIssuer() {
        assertThrows(CasValidationException::class.java) { validate(report(engine = "OTHER")) }
        assertThrows(CasValidationException::class.java) { validate(report(fileType = "UNLISTED")) }
    }

    @Test fun sourceHashAndStatementDateAreMandatoryAndValidated() {
        assertThrows(CasValidationException::class.java) { validate(report(sha = "abc")) }
        assertThrows(CasValidationException::class.java) { validate(report(end = "2026-99-99")) }
        assertThrows(CasValidationException::class.java) { validate(report(end = "2026-10-12")) }
    }

    @Test fun newHoldingsCannotBeSilentlyOpenedOrMappedByFuzzyName() {
        assertThrows(CasValidationException::class.java) { validate(report(listOf(row(name = "Other")))) }
        assertThrows(CasValidationException::class.java) {
            validate(report(), listOf(fund.copy(name = "Index Fund")))
        }
    }

    @Test fun ambiguousMatchingOwnerHoldingsCannotBeArbitrarilySelected() {
        assertThrows(CasValidationException::class.java) {
            validate(report(), listOf(fund, fund.copy(holdingId = HoldingId("duplicate"))))
        }
    }

    @Test fun familyOwnershipMustBeExplicitAndCannotCrossMemberBoundary() {
        assertThrows(CasValidationException::class.java) {
            validate(report(), listOf(fund.copy(ownerId = OwnerId("spouse"))))
        }
    }

    @Test fun adversarialMoneyFieldsRejectScientificGarbageAndNegatives() {
        for (bad in listOf("-1", "NaN", "Infinity", "1e400", "1; DROP TABLE", "999999999999999999999")) {
            assertThrows(CasValidationException::class.java) {
                validate(report(listOf(row(qty = bad))))
            }
        }
        assertThrows(CasValidationException::class.java) {
            validate(report(listOf(row(price = "-10"))))
        }
        assertThrows(CasValidationException::class.java) {
            validate(report(listOf(row(cost = "-1"))))
        }
    }

    @Test fun duplicateRowsRemainSeparateToBeCoalescedSafelyByReconciler() {
        val selected = validate(report(listOf(row(qty = "1"), row(qty = "2"))))
        assertEquals(2, selected.lines.size)
        val merged = SnapshotReconciler.coalesce(selected.lines)
        assertEquals(300_000_000L, merged.single().quantityScaled)
    }

    @Test fun multiEntryQuantityOverflowIsRejectedBeforeReview() {
        assertThrows(ArithmeticException::class.java) {
            validate(report(listOf(
                row(qty = "90000000000"), row(qty = "90000000000"),
            )))
        }
    }

    @Test fun emptyStatementsAndTooManyHoldingsAreBlocked() {
        assertThrows(CasValidationException::class.java) { validate(report(emptyList())) }
        assertThrows(CasValidationException::class.java) {
            validate(report(List(CasImportContract.MAX_PARSED_HOLDINGS + 1) { row() }))
        }
    }
}
