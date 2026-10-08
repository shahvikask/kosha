package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Guard against regression to three separate, torn family-book reads. */
class AtomicBookUseCaseTest {
    private val owner = OwnerId("self")
    private val holding = Holding(
        HoldingId("fund"), owner, null, "Synthetic Index Fund", "MUTUAL_FUND",
        QuantityScaled(0), MoneyScaled(1_000_000),
    )

    @Test fun portfolioReadsBookExactlyOnceAndNeverUsesSeparateGetters() = runTest {
        val repo = mockk<VaultRepository>()
        val book = VaultBook(
            listOf(FamilyMember(owner, "Synthetic Member", "Self", true)),
            listOf(holding),
            listOf(LedgerEvent(
                "buy", holding.id, owner, LedgerKind.BUY,
                "2026-01-01", 100_000_000, MoneyScaled(500_000), null,
                unitPrice = MoneyScaled(500_000),
            )),
        )
        coEvery { repo.readBook() } returns book

        val output = CalculateFamilyPortfolio(repo)(LocalDate.parse("2026-10-08"))
        assertEquals(1_000_000L, output.familyAssets.raw)
        assertEquals(owner, output.members.single().ownerId)
        coVerify(exactly = 1) { repo.readBook() }
        coVerify(exactly = 0) { repo.members() }
        coVerify(exactly = 0) { repo.holdings() }
        coVerify(exactly = 0) { repo.events() }
    }

    @Test fun snapshotPreviewUsesConsistentBookAndDoesNotAppendEvents() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.readBook() } returns VaultBook(
            listOf(FamilyMember(owner, "Synthetic Member", "Self", true)),
            listOf(holding),
            emptyList(),
        )
        val statement = StatementLine(
            holding.id, owner, LocalDate.parse("2026-10-08"),
            100_000_000, MoneyScaled(1_000_000),
        )
        val review = PreviewStatementReconciliation(repo)(
            owner, "a".repeat(64), listOf(statement),
        )
        assertEquals(ReconciliationOutcome.NEW_POSITION, review.lines.single().outcome)
        coVerify(exactly = 1) { repo.readBook() }
        coVerify(exactly = 0) { repo.appendFinancialEvents(any()) }
    }

    @Test fun legacyCorrectionWithoutReferenceDoesNotInventInvestmentCost() {
        val old = LedgerEvent(
            "legacy-cor", holding.id, owner, LedgerKind.CORRECTION,
            "2026-01-01", 100_000_000, MoneyScaled(50_000), null,
        )
        assertThrows(IllegalStateException::class.java) { old.toFinancialEvent() }
    }

    @Test fun richCorrectionPreservesPriceChargesAndSupersessionInDomain() {
        val rich = LedgerEvent(
            "corrected", holding.id, owner, LedgerKind.CORRECTION,
            "2026-01-01", 100_000_000, MoneyScaled(50_000), null,
            unitPrice = MoneyScaled(50_000),
            charges = MoneyScaled(100),
            supersedesEventId = "original",
        )
        val event = rich.toFinancialEvent()
        assertEquals(FinancialEventKind.CORRECTION, event.kind)
        assertEquals(MoneyScaled(50_000), event.unitPrice)
        assertEquals(MoneyScaled(100), event.charges)
        assertEquals("original", event.supersedesEventId)
    }

    @Test fun previewReceiptDoesNotImplicitlyCommitAnyStatement() = runTest {
        val repo = mockk<VaultRepository>()
        val source = StatementLine(
            holding.id, owner, LocalDate.parse("2026-10-08"),
            100_000_000, MoneyScaled(500_000),
        )
        val plan = SnapshotReconciler.review(
            listOf(source),
            listOf(FinancialHolding(holding.id, owner, holding.name, holding.assetCategory,
                holding.currentUnitPrice)),
            emptyList(), owner, "b".repeat(64),
        )
        val receipt = ReviewedStatement(
            "snap1", owner, listOf(source), "c".repeat(64), plan, "d".repeat(64),
        )
        coEvery { repo.prepareStatementReview(any(), any(), any(), any()) } returns receipt
        val reviewed = PrepareStatementReview(repo)(
            "snap1", owner, "b".repeat(64), listOf(source),
        )
        assertEquals(receipt, reviewed)
        coVerify(exactly = 1) { repo.prepareStatementReview(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.commitReviewedStatement(any()) }
    }

    @Test fun confirmedReceiptPassesThroughCommitUseCaseExactlyOnce() = runTest {
        val repo = mockk<VaultRepository>()
        val source = StatementLine(
            holding.id, owner, LocalDate.parse("2026-10-08"),
            0, MoneyScaled(1_000_000),
        )
        val plan = SnapshotReconciler.review(
            listOf(source),
            listOf(FinancialHolding(holding.id, owner, holding.name, holding.assetCategory,
                holding.currentUnitPrice)),
            emptyList(), owner, "e".repeat(64),
        )
        val receipt = ReviewedStatement(
            "snap2", owner, listOf(source), "a".repeat(64), plan, "b".repeat(64),
        )
        coEvery { repo.commitReviewedStatement(receipt) } returns SnapshotResult.IMPORTED
        assertEquals(SnapshotResult.IMPORTED, CommitReviewedStatement(repo)(receipt))
        coVerify(exactly = 1) { repo.commitReviewedStatement(receipt) }
        coVerify(exactly = 0) { repo.appendFinancialEvents(any()) }
    }
}
