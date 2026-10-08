package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.VaultHealth
import `in`.growurdhan.core.model.VaultStatus
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
