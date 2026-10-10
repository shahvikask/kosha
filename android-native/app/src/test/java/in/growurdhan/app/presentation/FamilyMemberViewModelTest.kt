package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class FamilyMemberViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val owner = FamilyMember(OwnerId("primary"), "Synthetic Primary", "Self", true)

    @Test fun oldFamilyReadIsDiscardedAfterCredentialReplacement() = runTest {
        val repo = mockk<VaultRepository>()
        val pending = CompletableDeferred<List<FamilyMember>>()
        coEvery { repo.members() } coAnswers { pending.await() }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        runCurrent()

        gate.lock()
        gate.grantDeviceCredential()
        pending.complete(listOf(owner))
        advanceUntilIdle()

        assertEquals(FamilyMemberUiState.Locked, vm.state.value)
    }

    @Test fun priorCredentialCannotPublishDelayedFamilySaveSuccess() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.members() } returns emptyList()
        val pending = CompletableDeferred<FamilyMember>()
        coEvery { repo.registerFamilyMember(any(), any()) } coAnswers { pending.await() }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage("Synthetic Primary", "Self"))
        gate.grantDeviceCredential()
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        runCurrent()

        gate.lock()
        gate.grantDeviceCredential()
        pending.complete(owner)
        advanceUntilIdle()

        assertEquals(FamilyMemberUiState.Locked, vm.state.value)
        coVerify(exactly = 1) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun lockedSessionCannotReadOrStageAnyFamilyInformation() = runTest {
        val repo = mockk<VaultRepository>()
        val vm = FamilyMemberViewModel(repo, VaultSessionGate())
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        assertEquals(FamilyMemberUiState.Locked, vm.state.value)
        vm.onIntent(FamilyMemberIntent.Stage("Synthetic", "Self"))
        coVerify(exactly = 0) { repo.members() }
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun openingFormReadsMembersButDoesNotCreateThem() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns emptyList()
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        assertEquals(FamilyMemberUiState.Ready(emptyList()), vm.state.value)
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun formRequiresNewCredentialAfterStagingNotJustRecentUnlock() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns emptyList()
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage(" Synthetic   Primary ", "Self"))
        assertTrue(vm.state.value is FamilyMemberUiState.AwaitingCredential)
        val pending = vm.state.value as FamilyMemberUiState.AwaitingCredential
        assertEquals("Synthetic Primary", pending.name)
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertEquals(FamilyMemberUiState.Locked, vm.state.value)
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun stagedFormIsSavedOnlyOnceAfterFreshCredential() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns emptyList()
        coEvery { repo.registerFamilyMember("Synthetic Primary", "Self") } returns owner
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage("Synthetic Primary", "Self"))
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
        gate.grantDeviceCredential() // simulated successful Android challenge
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertEquals(FamilyMemberUiState.Saved(owner), vm.state.value)
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.registerFamilyMember("Synthetic Primary", "Self") }
    }

    @Test fun invalidMemberAndDuplicateDisplayNameAreRejectedBeforeChallenge() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns listOf(owner)
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage("synthetic primary", "Child"))
        assertTrue(vm.state.value is FamilyMemberUiState.Ready)
        assertTrue((vm.state.value as FamilyMemberUiState.Ready).validationMessage != null)
        vm.onIntent(FamilyMemberIntent.Stage("Dependent", "Self"))
        assertTrue(vm.state.value is FamilyMemberUiState.Ready)
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun cancelClearsPendingIdentityEvenIfCredentialLaterSucceeds() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns emptyList()
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage("Synthetic Primary", "Self"))
        vm.onIntent(FamilyMemberIntent.Cancel)
        gate.grantDeviceCredential()
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        advanceUntilIdle()
        assertEquals(FamilyMemberUiState.Locked, vm.state.value)
        coVerify(exactly = 0) { repo.registerFamilyMember(any(), any()) }
    }

    @Test fun repositoryRejectionDoesNotPretendMemberWasSaved() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        coEvery { repo.members() } returns emptyList()
        coEvery { repo.registerFamilyMember(any(), any()) } throws IllegalStateException("private SQL error")
        val vm = FamilyMemberViewModel(repo, gate)
        vm.onIntent(FamilyMemberIntent.Refresh)
        advanceUntilIdle()
        vm.onIntent(FamilyMemberIntent.Stage("Synthetic Primary", "Self"))
        gate.grantDeviceCredential()
        vm.onIntent(FamilyMemberIntent.SaveAfterCredential)
        advanceUntilIdle()
        val state = vm.state.value as FamilyMemberUiState.Error
        assertTrue(state.message.contains("not confirmed"))
        assertTrue(!state.message.contains("private SQL error"))
        coVerify(exactly = 1) { repo.registerFamilyMember(any(), any()) }
    }
}
