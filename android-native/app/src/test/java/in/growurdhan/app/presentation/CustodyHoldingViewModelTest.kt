package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic records; this VM is not a credential authenticator. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustodyHoldingViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val owner = OwnerId("self")
    private val member = FamilyMember(owner, "Synthetic", "Self", true)
    private val account = CustodyAccount("account", owner, "CAMS", "Main synthetic")
    private fun book(accounts: List<CustodyAccount> = emptyList()) =
        VaultBook(listOf(member), emptyList(), emptyList(), accounts)

    @Test fun lockedRefreshNeverReadsEncryptedBook() = runTest {
        val repo = mockk<VaultRepository>()
        val viewModel = CustodyHoldingViewModel(repo, VaultSessionGate())
        viewModel.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        assertEquals(CustodyHoldingUiState.Locked, viewModel.state.value)
        coVerify(exactly = 0) { repo.readBook() }
    }

    @Test fun validAccountFormNeedsNewCredentialAfterStaging() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns book()
        val vm = CustodyHoldingViewModel(repo, gate)
        vm.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(CustodyHoldingIntent.StageAccount(owner, "CAMS", "Main"))
        assertTrue(vm.state.value is CustodyHoldingUiState.AwaitingCredential)
        vm.onIntent(CustodyHoldingIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertEquals(CustodyHoldingUiState.Locked, vm.state.value)
        coVerify(exactly = 0) { repo.registerCustodyAccount(any(), any(), any()) }
    }

    @Test fun freshCredentialAllowsOneExplicitRegistrarRegistration() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns book()
        coEvery { repo.registerCustodyAccount(owner, "CAMS", "Main") } returns account
        val vm = CustodyHoldingViewModel(repo, gate)
        vm.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(CustodyHoldingIntent.StageAccount(owner, "CAMS", "Main"))
        gate.grantDeviceCredential()
        vm.onIntent(CustodyHoldingIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertTrue(vm.state.value is CustodyHoldingUiState.Saved)
        vm.onIntent(CustodyHoldingIntent.SaveAfterCredential)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.registerCustodyAccount(owner, "CAMS", "Main") }
    }

    @Test fun invalidAndCrossOwnerEnrolmentCannotAskForCredential() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns book(listOf(account))
        val vm = CustodyHoldingViewModel(repo, gate)
        vm.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(CustodyHoldingIntent.StageHolding(
            OwnerId("other"), account.id, "Synthetic", "INF000000001", "123",
        ))
        assertTrue(vm.state.value is CustodyHoldingUiState.Ready)
        vm.onIntent(CustodyHoldingIntent.StageHolding(
            owner, account.id, "Synthetic", "invalid", "123",
        ))
        assertTrue(vm.state.value is CustodyHoldingUiState.Ready)
        coVerify(exactly = 0) { repo.registerCasHolding(any(), any(), any(), any(), any()) }
    }

    @Test fun approvedHoldingCreatesNoLedgerEventOrAutomaticStatementImport() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns book(listOf(account))
        val enrolled = Holding(
            HoldingId("new"), owner, account.id, "Synthetic fund",
            "MUTUAL_FUND", QuantityScaled(0), MoneyScaled(0),
        )
        coEvery {
            repo.registerCasHolding(owner, account.id, "Synthetic fund", "INF000000001", "123")
        } returns enrolled
        val vm = CustodyHoldingViewModel(repo, gate)
        vm.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(CustodyHoldingIntent.StageHolding(
            owner, account.id, "Synthetic fund", "INF000000001", "123",
        ))
        gate.grantDeviceCredential()
        vm.onIntent(CustodyHoldingIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertTrue(vm.state.value is CustodyHoldingUiState.Saved)
        coVerify(exactly = 1) {
            repo.registerCasHolding(owner, account.id, "Synthetic fund", "INF000000001", "123")
        }
        coVerify(exactly = 0) { repo.appendFinancialEvents(any()) }
        coVerify(exactly = 0) { repo.commitReviewedStatement(any()) }
    }

    @Test fun lockClearsStagedHoldingCredentialsAndForbidsPostLogoutWrite() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.readBook() } returns book(listOf(account))
        val vm = CustodyHoldingViewModel(repo, gate)
        vm.onIntent(CustodyHoldingIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(CustodyHoldingIntent.StageHolding(
            owner, account.id, "Synthetic", "INF000000001", "123",
        ))
        gate.lock()
        vm.clearSensitiveState()
        gate.grantDeviceCredential()
        vm.onIntent(CustodyHoldingIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertEquals(CustodyHoldingUiState.Locked, vm.state.value)
        coVerify(exactly = 0) { repo.registerCasHolding(any(), any(), any(), any(), any()) }
    }
}
