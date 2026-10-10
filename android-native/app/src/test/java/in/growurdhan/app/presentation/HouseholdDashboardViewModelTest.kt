package `in`.growurdhan.app.presentation

import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.Holding
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.QuantityScaled
import `in`.growurdhan.core.model.VaultBook
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * All records are synthetic. These tests exercise the same cancellation and
 * authorization boundaries used by the native activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdDashboardViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun book(price: Long = 150_000L): VaultBook {
        val owner = OwnerId("synthetic-primary")
        return VaultBook(
            members = listOf(FamilyMember(owner, "Synthetic Member", "Self", true)),
            holdings = listOf(
                Holding(
                    HoldingId("synthetic-holding"), owner, null, "Synthetic Fund",
                    "MUTUAL_FUND", QuantityScaled(100_000_000L), MoneyScaled(price),
                ),
            ),
            events = emptyList(),
        )
    }

    @Test fun lockedRefreshDoesNotReadTheEncryptedBook() = runTest {
        val repo = mockk<VaultRepository>()
        val vm = HouseholdDashboardViewModel(repo, VaultSessionGate())

        vm.refresh()
        advanceUntilIdle()

        assertEquals(HouseholdDashboardUiState.Locked, vm.state.value)
        coVerify(exactly = 0) { repo.readBook() }
    }

    @Test fun unlockedRefreshUsesOneBookSnapshotAndShowsRecordedValues() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.readBook() } returns book()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = HouseholdDashboardViewModel(repo, gate)

        vm.refresh()
        advanceUntilIdle()

        val ready = vm.state.value as HouseholdDashboardUiState.Ready
        assertEquals(150_000L, ready.dashboard.assets.raw)
        assertEquals(150_000L, ready.dashboard.netWorth.raw)
        assertEquals(1, ready.dashboard.memberCount)
        assertEquals(1, ready.dashboard.unknownCostHoldings)
        assertEquals("MUTUAL_FUND", ready.dashboard.byCategory.single().category)
        coVerify(exactly = 1) { repo.readBook() }
    }

    @Test fun lockCancelsSuspendedReadAndPreventsStaleFinancialState() = runTest {
        val repo = mockk<VaultRepository>()
        val delayed = CompletableDeferred<VaultBook>()
        coEvery { repo.readBook() } coAnswers { delayed.await() }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = HouseholdDashboardViewModel(repo, gate)

        vm.refresh()
        runCurrent()
        assertEquals(HouseholdDashboardUiState.Loading, vm.state.value)

        gate.lock()
        vm.lock()
        assertEquals(HouseholdDashboardUiState.Locked, vm.state.value)
        delayed.complete(book())
        advanceUntilIdle()

        assertEquals(HouseholdDashboardUiState.Locked, vm.state.value)
        coVerify(exactly = 1) { repo.readBook() }
    }

    @Test fun newerRefreshCannotBeOverwrittenByEarlierSuspendedRead() = runTest {
        val repo = mockk<VaultRepository>()
        val earlier = CompletableDeferred<VaultBook>()
        var reads = 0
        coEvery { repo.readBook() } coAnswers {
            reads++
            if (reads == 1) earlier.await() else book(240_000L)
        }
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = HouseholdDashboardViewModel(repo, gate)

        vm.refresh()
        runCurrent()
        vm.refresh()
        advanceUntilIdle()

        val ready = vm.state.value as HouseholdDashboardUiState.Ready
        assertEquals(240_000L, ready.dashboard.netWorth.raw)
        earlier.complete(book(90_000L))
        advanceUntilIdle()
        assertEquals(240_000L, (vm.state.value as HouseholdDashboardUiState.Ready).dashboard.netWorth.raw)
        coVerify(exactly = 2) { repo.readBook() }
    }

    @Test fun repositoryFailureDoesNotExposePrivateErrorDetails() = runTest {
        val repo = mockk<VaultRepository>()
        coEvery { repo.readBook() } throws IllegalStateException("synthetic private account detail")
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val vm = HouseholdDashboardViewModel(repo, gate)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(HouseholdDashboardUiState.Unavailable, vm.state.value)
        assertTrue(vm.state.value.toString().contains("synthetic private account detail").not())
    }
}
