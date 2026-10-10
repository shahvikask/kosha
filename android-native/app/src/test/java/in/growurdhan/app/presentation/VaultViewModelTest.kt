package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.VaultHealth
import `in`.growurdhan.core.model.VaultStatus
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun openingIsExplicitAndInitialRefreshMustNotCreateAnything() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.status() } returns VaultStatus(false, false, 0)
        coEvery { repo.openOrCreate() } returns VaultStatus(true, true, 1)
        coEvery { repo.health() } returns VaultHealth("ok", 0, 0, 0, 0)
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)

        advanceUntilIdle()
        assertEquals(VaultUiState.NotCreated, vm.state.value)
        coVerify(exactly = 0) { repo.openOrCreate() }

        gate.grantDeviceCredential()
        vm.onIntent(VaultIntent.OpenOrCreate)
        advanceUntilIdle()
        assertEquals(VaultUiState.Ready(VaultHealth("ok", 0, 0, 0, 0)), vm.state.value)
        coVerify(exactly = 1) { repo.openOrCreate() }
    }

    @Test fun unauthorizedDirectVaultOpenNeverTouchesRepository() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate()
        coEvery { repo.status() } returns VaultStatus(false, false, 0)
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        vm.onIntent(VaultIntent.OpenOrCreate)
        advanceUntilIdle()
        assertEquals(VaultUiState.Error(
            "The encrypted vault could not be opened or verified. Existing files were preserved.",
        ), vm.state.value)
        coVerify(exactly = 0) { repo.openOrCreate() }
    }

    @Test fun closingVaultRevokesSessionAndMustCloseDatabase() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.openOrCreate() } returns VaultStatus(true, true, 2)
        coEvery { repo.health() } returns VaultHealth("ok", 0, 0, 0, 0)
        coEvery { repo.close() } returns Unit
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        gate.grantDeviceCredential()
        vm.onIntent(VaultIntent.OpenOrCreate)
        advanceUntilIdle()
        vm.onIntent(VaultIntent.Close)
        advanceUntilIdle()
        assertEquals(VaultUiState.Closed, vm.state.value)
        assertEquals(false, gate.isUnlocked())
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun closingDuringRestorePickerDoesNotInventAnInitializedVault() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate()
        coEvery { repo.status() } returns VaultStatus(false, false, 0)
        coEvery { repo.close() } returns Unit
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        assertEquals(VaultUiState.NotCreated, vm.state.value)
        vm.onIntent(VaultIntent.Close)
        advanceUntilIdle()
        assertEquals(VaultUiState.NotCreated, vm.state.value)
        coVerify(exactly = 0) { repo.openOrCreate() }
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun closingWhileOpenIsSuspendedCannotRestoreReadyState() = runTest {
        val repo = mockk<VaultRepository>()
        val pendingOpen = CompletableDeferred<VaultStatus>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.openOrCreate() } coAnswers { pendingOpen.await() }
        coEvery { repo.health() } returns VaultHealth("ok", 0, 0, 0, 0)
        coEvery { repo.close() } returns Unit
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        gate.grantDeviceCredential()

        vm.onIntent(VaultIntent.OpenOrCreate)
        runCurrent()
        vm.onIntent(VaultIntent.Close)
        // Revocation is synchronous, not dependent on coroutine scheduling.
        assertEquals(false, gate.isUnlocked())
        // The close must wait for the in-flight open, then close its connection.
        pendingOpen.complete(VaultStatus(true, true, 2))
        advanceUntilIdle()
        assertEquals(VaultUiState.Closed, vm.state.value)
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun expiringSessionWhileHealthIsSuspendedCannotShowReady() = runTest {
        val repo = mockk<VaultRepository>()
        val pendingHealth = CompletableDeferred<VaultHealth>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.openOrCreate() } returns VaultStatus(true, true, 2)
        coEvery { repo.health() } coAnswers { pendingHealth.await() }
        coEvery { repo.close() } returns Unit
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        gate.grantDeviceCredential()

        vm.onIntent(VaultIntent.OpenOrCreate)
        runCurrent()
        gate.lock()
        pendingHealth.complete(VaultHealth("ok", 0, 0, 0, 0))
        advanceUntilIdle()

        assertEquals(VaultUiState.Closed, vm.state.value)
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun sessionExpiryDuringOpenClosesDatabaseWithoutSeparateCloseIntent() = runTest {
        val repo = mockk<VaultRepository>()
        val pendingOpen = CompletableDeferred<VaultStatus>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.openOrCreate() } coAnswers { pendingOpen.await() }
        coEvery { repo.close() } returns Unit
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        gate.grantDeviceCredential()

        vm.onIntent(VaultIntent.OpenOrCreate)
        runCurrent()
        gate.lock()
        pendingOpen.complete(VaultStatus(true, true, 2))
        advanceUntilIdle()

        assertEquals(VaultUiState.Closed, vm.state.value)
        coVerify(exactly = 1) { repo.close() }
        coVerify(exactly = 0) { repo.health() }
    }

    @Test fun queuedCloseIsNotSkippedBySubsequentRefresh() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.close() } returns Unit
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()

        vm.onIntent(VaultIntent.Close)
        vm.onIntent(VaultIntent.Refresh)
        advanceUntilIdle()

        assertEquals(false, gate.isUnlocked())
        assertEquals(VaultUiState.Closed, vm.state.value)
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun missingDeviceCredentialRevokesSessionAndClosesConnection() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.close() } returns Unit
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()

        vm.onIntent(VaultIntent.CredentialUnavailable)
        assertEquals(false, gate.isUnlocked())
        advanceUntilIdle()

        assertEquals(
            VaultUiState.Error("Set up a secure device screen lock to access your encrypted vault."),
            vm.state.value,
        )
        coVerify(exactly = 1) { repo.close() }
    }

    @Test fun refreshDoesNotReadStatusWhileVaultOpenIsInProgress() = runTest {
        val repo = mockk<VaultRepository>()
        val pendingOpen = CompletableDeferred<VaultStatus>()
        coEvery { repo.status() } returns VaultStatus(true, true, 2)
        coEvery { repo.openOrCreate() } coAnswers { pendingOpen.await() }
        coEvery { repo.health() } returns VaultHealth("ok", 0, 0, 0, 0)
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        gate.grantDeviceCredential()

        vm.onIntent(VaultIntent.OpenOrCreate)
        runCurrent()
        vm.onIntent(VaultIntent.Refresh)
        runCurrent()

        // The initial status read has finished. Refresh must wait for open.
        coVerify(exactly = 1) { repo.status() }
        pendingOpen.complete(VaultStatus(true, true, 2))
        advanceUntilIdle()
        coVerify(exactly = 2) { repo.status() }
        assertEquals(VaultUiState.Ready(VaultHealth("ok", 0, 0, 0, 0)), vm.state.value)
    }

    @Test fun refreshDoesNotReadStatusWhileCloseIsInProgress() = runTest {
        val repo = mockk<VaultRepository>()
        val pendingClose = CompletableDeferred<Unit>()
        coEvery { repo.status() } returns VaultStatus(true, false, 2)
        coEvery { repo.close() } coAnswers { pendingClose.await() }
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()

        vm.onIntent(VaultIntent.Close)
        runCurrent()
        vm.onIntent(VaultIntent.Refresh)
        runCurrent()

        coVerify(exactly = 1) { repo.status() }
        pendingClose.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 3) { repo.status() }
        assertEquals(VaultUiState.Closed, vm.state.value)
    }

    @Test fun damagedVaultIsReportedRatherThanSilentlyReset() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.status() } throws IllegalStateException("missing device key")
        val gate = VaultSessionGate()
        val vm = VaultViewModel(repo, gate)
        advanceUntilIdle()
        val state = vm.state.value as VaultUiState.Error
        assertEquals(
            "The encrypted vault could not be opened or verified. Existing files were preserved.",
            state.message,
        )
        coVerify(exactly = 0) { repo.openOrCreate() }
    }
}
