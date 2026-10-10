package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.CasHoldingIdentity
import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.CustodyAccount
import `in`.growurdhan.core.model.VaultBook
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.SnapshotResult
import `in`.growurdhan.domain.finance.FinancialHolding
import `in`.growurdhan.domain.finance.ReviewedStatement
import `in`.growurdhan.domain.finance.SnapshotReconciler
import `in`.growurdhan.domain.finance.StatementLine
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatementReviewViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val owner = OwnerId("self")
    private val holdingId = HoldingId("holding")
    private val line = StatementLine(
        holdingId, owner, LocalDate.parse("2026-10-08"), 100_000_000L, MoneyScaled(100_000),
    )
    private val sha = "a".repeat(64)
    private fun receipt(): ReviewedStatement {
        val plan = SnapshotReconciler.review(
            listOf(line),
            listOf(FinancialHolding(holdingId, owner, "Synthetic", "MUTUAL_FUND", MoneyScaled(100_000))),
            emptyList(), owner, sha,
        )
        return ReviewedStatement("snap", owner, listOf(line), "b".repeat(64), plan, "c".repeat(64))
    }

    @Test fun reauthenticationCannotPublishOldStatementPreview() = runTest {
        val repo = mockk<VaultRepository>()
        val pending = CompletableDeferred<ReviewedStatement>()
        coEvery { repo.prepareStatementReview(any(), any(), any(), any()) } coAnswers {
            pending.await()
        }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = StatementReviewViewModel(repo, gate)
        vm.prepareFromValidatedParser("snap", owner, sha, listOf(line))
        runCurrent()
        gate.lock()
        gate.grantDeviceCredential()
        pending.complete(receipt())
        advanceUntilIdle()

        assertEquals(StatementReviewUiState.Empty, vm.state.value)
        coVerify(exactly = 0) { repo.commitReviewedStatement(any()) }
    }

    @Test fun newlyAuthenticatedSessionCannotReuseOldFamilyOwnerList() = runTest {
        val repo = mockk<VaultRepository>()
        val pending = CompletableDeferred<VaultBook>()
        coEvery { repo.readBook() } coAnswers { pending.await() }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = StatementReviewViewModel(repo, gate)
        vm.onIntent(StatementReviewIntent.LoadOwners)
        runCurrent()
        gate.lock()
        gate.grantDeviceCredential()
        pending.complete(VaultBook(
            listOf(FamilyMember(owner, "Synthetic private member", "Self", true)),
            emptyList(), emptyList(),
        ))
        advanceUntilIdle()

        assertEquals(StatementReviewUiState.Empty, vm.state.value)
    }

    @Test fun unauthenticatedPreviewNeverReadsFromVault() = runTest {
        val repo = mockk<VaultRepository>()
        val vm = StatementReviewViewModel(repo, VaultSessionGate())
        vm.prepareFromValidatedParser("snap", owner, sha, listOf(line))
        advanceUntilIdle()
        assertTrue(vm.state.value is StatementReviewUiState.Error)
        coVerify(exactly = 0) { repo.prepareStatementReview(any(), any(), any(), any()) }
    }

    @Test fun previewNeedsExplicitApprovalAndCanBeCancelledWithoutWrites() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val receipt = receipt()
        coEvery { repo.prepareStatementReview(any(), any(), any(), any()) } returns receipt
        val vm = StatementReviewViewModel(repo, gate)
        vm.prepareFromValidatedParser("snap", owner, sha, listOf(line))
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.Reviewing(receipt), vm.state.value)
        vm.onIntent(StatementReviewIntent.RequestApproval)
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.commitReviewedStatement(any()) }
        vm.onIntent(StatementReviewIntent.Cancel)
        assertEquals(StatementReviewUiState.Empty, vm.state.value)
    }

    @Test fun confirmedCredentialCommitsOnceThenForgetsReceipt() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val receipt = receipt()
        coEvery { repo.prepareStatementReview(any(), any(), any(), any()) } returns receipt
        coEvery { repo.commitReviewedStatement(receipt) } returns SnapshotResult.IMPORTED
        val vm = StatementReviewViewModel(repo, gate)
        vm.prepareFromValidatedParser("snap", owner, sha, listOf(line))
        advanceUntilIdle()
        vm.onIntent(StatementReviewIntent.CommitAfterCredential)
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.Completed(SnapshotResult.IMPORTED), vm.state.value)
        vm.onIntent(StatementReviewIntent.CommitAfterCredential)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.commitReviewedStatement(receipt) }
    }

    @Test fun lockingClearsReceiptAndBlocksCommit() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.prepareStatementReview(any(), any(), any(), any()) } returns receipt()
        val vm = StatementReviewViewModel(repo, gate)
        vm.prepareFromValidatedParser("snap", owner, sha, listOf(line))
        advanceUntilIdle()
        gate.lock()
        vm.clearSensitiveState()
        vm.onIntent(StatementReviewIntent.CommitAfterCredential)
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.Empty, vm.state.value)
        coVerify(exactly = 0) { repo.commitReviewedStatement(any()) }
    }
    @Test fun noFamilyOwnerPromptsFamilyEnrollmentWithoutImport() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns VaultBook(emptyList(), emptyList(), emptyList())
        val vm = StatementReviewViewModel(repo, gate)
        vm.onIntent(StatementReviewIntent.LoadOwners)
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.MissingFamilyMember, vm.state.value)
        coVerify(exactly = 0) { repo.prepareStatementReview(any(), any(), any(), any()) }
    }

    @Test fun familyWithoutEnrolledHoldingIsSentToCustodySetup() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns VaultBook(
            listOf(FamilyMember(owner, "Synthetic", "Self", true)), emptyList(), emptyList(),
        )
        val vm = StatementReviewViewModel(repo, gate)
        vm.onIntent(StatementReviewIntent.LoadOwners)
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.MissingCasMapping, vm.state.value)
        coVerify(exactly = 0) { repo.prepareStatementReview(any(), any(), any(), any()) }
    }

    @Test fun onlyOwnersWithEnrolledCasIdentityCanChooseAPdf() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val spouse = OwnerId("spouse")
        val primary = FamilyMember(owner, "Synthetic Primary", "Self", true)
        val other = FamilyMember(spouse, "Synthetic Spouse", "Spouse", false)
        val account = CustodyAccount("a", owner, "CAMS", "Synthetic")
        val mapped = CasHoldingIdentity(
            holdingId, owner, account.id, "INF000000001", "123", "CAMS",
        )
        coEvery { repo.readBook() } returns VaultBook(
            listOf(primary, other), emptyList(), emptyList(), listOf(account), listOf(mapped),
        )
        val vm = StatementReviewViewModel(repo, gate)
        vm.onIntent(StatementReviewIntent.LoadOwners)
        advanceUntilIdle()
        assertEquals(StatementReviewUiState.SelectOwner(listOf(primary)), vm.state.value)
    }

}
