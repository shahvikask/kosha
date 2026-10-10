package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.VaultHealth
import `in`.growurdhan.core.model.VaultStatus
import `in`.growurdhan.domain.vault.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Invalidates delayed results without interrupting a vault database transaction. */
    private var requestGeneration: Long = 0L
    // Close must wait for any already-started open; otherwise a late open can
    // leave the encrypted database connected after the user has locked it.
    private val vaultOperationMutex = Mutex()
    private var lastCleanupOperation: Job? = null

    /**
     * The document picker may return immediately after the OS credential
     * challenge, while a previously dispatched Close is still pending.
     * Wait for that mandatory cleanup before an Activity-owned import opens.
     */
    suspend fun awaitPendingCleanup() {
        lastCleanupOperation?.join()
    }

    init { onIntent(VaultIntent.Refresh) }

    fun onIntent(intent: VaultIntent) {
        val request = ++requestGeneration
        // Revoke authorization immediately, rather than waiting for a coroutine to run.
        if (intent == VaultIntent.Close || intent == VaultIntent.CredentialUnavailable) {
            session.lock()
            mutableState.value = VaultUiState.Loading
        }
        val operation = viewModelScope.launch {
            // Closing is mandatory cleanup even when a later refresh/open supersedes
            // its UI result. Otherwise a queued close could be silently dropped.
            if (request != requestGeneration && intent != VaultIntent.Close &&
                intent != VaultIntent.CredentialUnavailable) return@launch
            if (request == requestGeneration) mutableState.value = VaultUiState.Loading
            try {
                when (intent) {
                    VaultIntent.Refresh -> {
                        vaultOperationMutex.withLock {
                            // Never inspect SQLCipher status/health while another
                            // coroutine is opening or closing the connection.
                            if (request != requestGeneration) return@withLock
                            val info = repository.status()
                            val next = when {
                                !info.initialized -> VaultUiState.NotCreated
                                !info.opened -> VaultUiState.Closed
                                !session.isUnlocked() -> {
                                    // A status refresh must not leave an already-open
                                    // connection behind after authorization expires.
                                    repository.close()
                                    VaultUiState.Closed
                                }
                                else -> {
                                    val health = repository.health()
                                    if (!session.isUnlocked()) {
                                        repository.close()
                                        VaultUiState.Closed
                                    } else VaultUiState.Ready(health)
                                }
                            }
                            if (request == requestGeneration) {
                                if (next is VaultUiState.Ready && !session.isUnlocked()) {
                                    // Authorization may expire between computing the
                                    // result and publishing it. Close before returning.
                                    repository.close()
                                    mutableState.value = VaultUiState.Closed
                                } else {
                                    mutableState.value = next
                                }
                            }
                        }
                    }
                    VaultIntent.CredentialUnavailable -> {
                        // Missing/revoked device security must also close any prior
                        // SQLCipher connection, not just replace the screen message.
                        vaultOperationMutex.withLock { repository.close() }
                        if (request == requestGeneration) mutableState.value = VaultUiState.Error(
                            "Set up a secure device screen lock to access your encrypted vault.",
                        )
                    }
                    VaultIntent.OpenOrCreate -> {
                        check(session.isUnlocked()) {
                            "Device authentication is required to open the native vault."
                        }
                        vaultOperationMutex.withLock {
                            // A close request may have revoked the credential while
                            // this open was waiting for the preceding operation.
                            if (request != requestGeneration || !session.isUnlocked()) return@withLock
                            repository.openOrCreate()
                            // An expired credential must close the physical connection,
                            // even when no explicit Close intent has been dispatched.
                            if (!session.isUnlocked()) {
                                repository.close()
                                if (request == requestGeneration) mutableState.value = VaultUiState.Closed
                                return@withLock
                            }
                            val health = repository.health()
                            if (!session.isUnlocked()) {
                                repository.close()
                                if (request == requestGeneration) mutableState.value = VaultUiState.Closed
                                return@withLock
                            }
                            if (request == requestGeneration) {
                                if (!session.isUnlocked()) {
                                    // Check once more immediately before publishing
                                    // financial access to the UI.
                                    repository.close()
                                    mutableState.value = VaultUiState.Closed
                                } else {
                                    mutableState.value = VaultUiState.Ready(health)
                                }
                            }
                        }
                    }
                    VaultIntent.Close -> {
                        vaultOperationMutex.withLock {
                            repository.close()
                            // A cancelled picker must not invent an initialized vault.
                            val initialized = repository.status().initialized
                            if (request == requestGeneration) {
                                mutableState.value = if (initialized)
                                    VaultUiState.Closed else VaultUiState.NotCreated
                            }
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
        if (intent == VaultIntent.Close || intent == VaultIntent.CredentialUnavailable) {
            lastCleanupOperation = operation
        }
    }
}
