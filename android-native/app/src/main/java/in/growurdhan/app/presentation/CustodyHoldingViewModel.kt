package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.VaultBook
import `in`.growurdhan.domain.vault.CustodyHoldingOnboarding
import `in`.growurdhan.domain.vault.VaultRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CustodyHoldingUiState {
    data object Locked : CustodyHoldingUiState
    data object Loading : CustodyHoldingUiState
    data class Ready(val book: VaultBook, val error: String? = null) : CustodyHoldingUiState
    /** Volatile request requiring OS-confirmed credential AFTER this request was staged. */
    data class AwaitingCredential(
        val request: CustodyRegistration,
        val stagedEpoch: Long,
    ) : CustodyHoldingUiState
    data object Saving : CustodyHoldingUiState
    data class Saved(val message: String) : CustodyHoldingUiState
    data class Error(val message: String) : CustodyHoldingUiState
}

sealed interface CustodyRegistration {
    data class Account(
        val ownerId: OwnerId, val type: String, val label: String,
    ) : CustodyRegistration
    data class Holding(
        val ownerId: OwnerId, val accountId: String,
        val name: String, val isin: String, val folio: String,
    ) : CustodyRegistration
}

sealed interface CustodyHoldingIntent {
    data object Refresh : CustodyHoldingIntent
    data class StageAccount(
        val ownerId: OwnerId, val type: String, val label: String,
    ) : CustodyHoldingIntent
    data class StageHolding(
        val ownerId: OwnerId, val accountId: String,
        val name: String, val isin: String, val folio: String,
    ) : CustodyHoldingIntent
    /** Call only after ActivityResult.RESULT_OK for an OS device credential. */
    data object SaveAfterCredential : CustodyHoldingIntent
    data object Cancel : CustodyHoldingIntent
}

/**
 * Read-only forms until explicit user submission and newer device credential.
 * All account/holding mutations are revalidated in one SQLCipher transaction;
 * no fake quantities or implied historical purchases are created.
 */
@HiltViewModel
class CustodyHoldingViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CustodyHoldingUiState>(CustodyHoldingUiState.Locked)
    val state: StateFlow<CustodyHoldingUiState> = mutableState
    private var operation: Job? = null
    private var generation = 0L

    fun onIntent(intent: CustodyHoldingIntent) {
        when (intent) {
            CustodyHoldingIntent.Refresh -> refresh()
            is CustodyHoldingIntent.StageAccount -> stageAccount(intent)
            is CustodyHoldingIntent.StageHolding -> stageHolding(intent)
            CustodyHoldingIntent.SaveAfterCredential -> saveAfterCredential()
            CustodyHoldingIntent.Cancel -> clearSensitiveState()
        }
    }

    private fun refresh() {
        operation?.cancel()
        val expectedGeneration = ++generation
        val originalRevision = session.credentialEpoch()
        if (!session.isUnlocked()) {
            clearSensitiveState()
            return
        }
        mutableState.value = CustodyHoldingUiState.Loading
        operation = viewModelScope.launch {
            try {
                val book = vault.readBook()
                if (expectedGeneration == generation) {
                    if (session.isCurrentSession(originalRevision)) {
                        mutableState.value = CustodyHoldingUiState.Ready(book)
                    } else {
                        clearSensitiveState()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (expectedGeneration == generation) mutableState.value =
                    CustodyHoldingUiState.Error("Could not read encrypted accounts. Reopen the vault.")
            }
        }
    }

    private fun stageAccount(intent: CustodyHoldingIntent.StageAccount) {
        val ready = mutableState.value as? CustodyHoldingUiState.Ready ?: return
        if (!session.isUnlocked()) { clearSensitiveState(); return }
        try {
            require(ready.book.members.any { it.id == intent.ownerId })
            val draft = CustodyHoldingOnboarding.account(
                intent.ownerId, intent.type, intent.label,
                UUID.randomUUID().toString(), ready.book.accounts,
            )
            mutableState.value = CustodyHoldingUiState.AwaitingCredential(
                CustodyRegistration.Account(draft.ownerId, draft.accountType, draft.identifierMasked),
                session.credentialEpoch(),
            )
        } catch (_: IllegalArgumentException) {
            mutableState.value = ready.copy(error = "Select an existing family owner and a unique, valid registrar nickname.")
        }
    }

    private fun stageHolding(intent: CustodyHoldingIntent.StageHolding) {
        val ready = mutableState.value as? CustodyHoldingUiState.Ready ?: return
        if (!session.isUnlocked()) { clearSensitiveState(); return }
        try {
            require(ready.book.members.any { it.id == intent.ownerId })
            val account = ready.book.accounts.single {
                it.id == intent.accountId && it.ownerId == intent.ownerId
            }
            val (_, identity) = CustodyHoldingOnboarding.holding(
                intent.ownerId, account, intent.name, intent.isin, intent.folio,
                HoldingId(UUID.randomUUID().toString()), ready.book.casIdentities,
            )
            mutableState.value = CustodyHoldingUiState.AwaitingCredential(
                CustodyRegistration.Holding(
                    intent.ownerId, intent.accountId, intent.name.trim(),
                    identity.isin, identity.folio,
                ),
                session.credentialEpoch(),
            )
        } catch (_: Exception) {
            mutableState.value = ready.copy(error =
                "Choose an owned registrar account and enter a unique fund name, ISIN and folio.")
        }
    }

    private fun saveAfterCredential() {
        val pending = mutableState.value as? CustodyHoldingUiState.AwaitingCredential ?: return
        if (!session.canCommitFinancialChanges() ||
            session.credentialEpoch() <= pending.stagedEpoch
        ) {
            clearSensitiveState()
            return
        }
        operation?.cancel()
        val expectedGeneration = ++generation
        val originalRevision = session.credentialEpoch()
        mutableState.value = CustodyHoldingUiState.Saving
        operation = viewModelScope.launch {
            try {
                val notice = when (val request = pending.request) {
                    is CustodyRegistration.Account -> {
                        vault.registerCustodyAccount(request.ownerId, request.type, request.label)
                        "Registrar account saved in the encrypted vault."
                    }
                    is CustodyRegistration.Holding -> {
                        vault.registerCasHolding(
                            request.ownerId, request.accountId, request.name,
                            request.isin, request.folio,
                        )
                        "Fund enrolled for future exact CAS identity matching."
                    }
                }
                if (expectedGeneration == generation) {
                    if (session.isCurrentSession(originalRevision)) {
                        mutableState.value = CustodyHoldingUiState.Saved(notice)
                    } else {
                        clearSensitiveState()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (expectedGeneration == generation) {
                    mutableState.value = CustodyHoldingUiState.Error(
                        "The entry was not confirmed. Reload the encrypted book to check existing accounts.",
                    )
                }
            }
        }
    }

    fun clearSensitiveState() {
        ++generation
        operation?.cancel()
        operation = null
        mutableState.value = CustodyHoldingUiState.Locked
    }

    override fun onCleared() {
        clearSensitiveState()
        super.onCleared()
    }
}
