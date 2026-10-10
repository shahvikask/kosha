package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.domain.finance.HouseholdDashboard
import `in`.growurdhan.domain.finance.HouseholdDashboardProjector
import `in`.growurdhan.domain.finance.PortfolioEngine
import `in`.growurdhan.domain.finance.toFinancialEvent
import `in`.growurdhan.domain.finance.toFinancialHolding
import `in`.growurdhan.domain.vault.VaultRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface HouseholdDashboardUiState {
    data object Locked : HouseholdDashboardUiState
    data object Loading : HouseholdDashboardUiState
    data class Ready(val dashboard: HouseholdDashboard) : HouseholdDashboardUiState
    data object Unavailable : HouseholdDashboardUiState
}

/**
 * Read-only presentation boundary. The repository performs an atomic encrypted
 * book read; no sensitive book is cached in SavedStateHandle or preferences.
 */
@HiltViewModel
class HouseholdDashboardViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HouseholdDashboardUiState>(HouseholdDashboardUiState.Locked)
    val state: StateFlow<HouseholdDashboardUiState> = mutableState
    private var generation = 0L
    private var operation: Job? = null

    /** Revoke financial UI data synchronously, even while an I/O read is suspended. */
    fun lock() {
        generation++
        operation?.cancel()
        operation = null
        mutableState.value = HouseholdDashboardUiState.Locked
    }

    fun refresh() {
        if (!session.isUnlocked()) {
            lock()
            return
        }
        operation?.cancel()
        val request = ++generation
        mutableState.value = HouseholdDashboardUiState.Loading
        operation = viewModelScope.launch {
            try {
                check(session.isUnlocked())
                val book = repository.readBook()
                check(session.isUnlocked())
                val report = PortfolioEngine.derive(
                    holdings = book.holdings.map { it.toFinancialHolding() },
                    events = book.events.map { it.toFinancialEvent() },
                    asOf = LocalDate.now(),
                    knownMembers = book.members.map { it.id },
                )
                if (request != generation) return@launch
                if (session.isUnlocked()) {
                    mutableState.value = HouseholdDashboardUiState.Ready(
                        HouseholdDashboardProjector.from(report),
                    )
                } else lock()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request != generation) return@launch
                mutableState.value = if (session.isUnlocked()) HouseholdDashboardUiState.Unavailable
                    else HouseholdDashboardUiState.Locked
            }
        }
    }
}
