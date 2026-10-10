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
    data object CredentialUnavailable : VaultIntent
}

/**
 * Native encrypted-vault state. The Activity obtains Android credential confirmation
 * before issuing OpenOrCreate; this ViewModel independently checks the short-lived
 * in-memory session gate. SQLCipher's Keystore key is NOT the login credential.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val state: StateFlow<VaultUiState> = mutableState

    init { onIntent(VaultIntent.Refresh) }

    /** Invalidates delayed results without interrupting a vault database transaction. */
    private var requestGeneration: Long = 0L

    fun onIntent(intent: VaultIntent) {
        val request = ++requestGeneration
        // Revoke authorization immediately, rather than waiting for a coroutine to run.
        if (intent == VaultIntent.Close || intent == VaultIntent.CredentialUnavailable) {
            session.lock()
            mutableState.value = VaultUiState.Loading
        }
        viewModelScope.launch {
            if (request != requestGeneration) return@launch
            mutableState.value = VaultUiState.Loading
            try {
                when (intent) {
                    VaultIntent.Refresh -> {
                        val info = repository.status()
                        val next = when {
                            !info.initialized -> VaultUiState.NotCreated
                            !info.opened || !session.isUnlocked() -> VaultUiState.Closed
                            else -> VaultUiState.Ready(repository.health())
                        }
                        if (request == requestGeneration) {
                            mutableState.value = if (next is VaultUiState.Ready && !session.isUnlocked())
                                VaultUiState.Closed else next
                        }
                    }
                    VaultIntent.CredentialUnavailable -> {
                        if (request == requestGeneration) mutableState.value = VaultUiState.Error(
                            "Set up a secure device screen lock to access your encrypted vault.",
                        )
                    }
                    VaultIntent.OpenOrCreate -> {
                        check(session.isUnlocked()) {
                            "Device authentication is required to open the native vault."
                        }
                        repository.openOrCreate()
                        val health = repository.health()
                        if (request == requestGeneration) {
                            mutableState.value = if (session.isUnlocked())
                                VaultUiState.Ready(health) else VaultUiState.Closed
                        }
                    }
                    VaultIntent.Close -> {
                        repository.close()
                        // The document chooser can revoke a session on a new
                        // device before any vault exists. Closing it must not
                        // fabricate a "closed but initialized" status.
                        val initialized = repository.status().initialized
                        if (request == requestGeneration) {
                            mutableState.value = if (initialized)
                                VaultUiState.Closed else VaultUiState.NotCreated
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == requestGeneration) mutableState.value = VaultUiState.Error(
                    "The encrypted vault could not be opened or verified. Existing files were preserved.",
                )
            }
        }
    }
}
