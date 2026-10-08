package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.VaultHealth
import `in`.growurdhan.core.model.VaultStatus
import `in`.growurdhan.domain.vault.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data object NotCreated : VaultUiState
    data object Closed : VaultUiState
    data class Ready(val health: VaultHealth) : VaultUiState
    data class Error(val message: String) : VaultUiState
}

sealed interface VaultIntent {
    data object OpenOrCreate : VaultIntent
    data object Close : VaultIntent
    data object Refresh : VaultIntent
}

/**
 * M2 development-only vault controls. No portfolio, password or token is exposed
 * to saved UI state. Device-bound storage != an authenticated app session.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val state: StateFlow<VaultUiState> = mutableState

    init { onIntent(VaultIntent.Refresh) }

    fun onIntent(intent: VaultIntent) {
        viewModelScope.launch {
            mutableState.value = VaultUiState.Loading
            try {
                when (intent) {
                    VaultIntent.Refresh -> {
                        val info = repository.status()
                        mutableState.value = when {
                            !info.initialized -> VaultUiState.NotCreated
                            !info.opened -> VaultUiState.Closed
                            else -> VaultUiState.Ready(repository.health())
                        }
                    }
                    VaultIntent.OpenOrCreate -> {
                        repository.openOrCreate()
                        mutableState.value = VaultUiState.Ready(repository.health())
                    }
                    VaultIntent.Close -> {
                        repository.close()
                        mutableState.value = VaultUiState.Closed
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = VaultUiState.Error(
                    "The local encrypted vault could not be opened. Existing files were not erased.",
                )
            }
        }
    }
}
