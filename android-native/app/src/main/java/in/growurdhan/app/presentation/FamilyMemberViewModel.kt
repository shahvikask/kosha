package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.vault.FamilyMemberOnboarding
import `in`.growurdhan.domain.vault.VaultRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface FamilyMemberUiState {
    data object Locked : FamilyMemberUiState
    data object Loading : FamilyMemberUiState
    data class Ready(
        val members: List<FamilyMember>,
        val validationMessage: String? = null,
    ) : FamilyMemberUiState
    /** Volatile pending request; not SavedStateHandle or persistent preferences. */
    data class AwaitingCredential(val name: String, val relationship: String) : FamilyMemberUiState
    data object Saving : FamilyMemberUiState
    data class Saved(val member: FamilyMember) : FamilyMemberUiState
    data class Error(val message: String) : FamilyMemberUiState
}

sealed interface FamilyMemberIntent {
    data object Refresh : FamilyMemberIntent
    data class Stage(val displayName: String, val relationship: String) : FamilyMemberIntent
    /** Only the Activity's successful Android credential callback can invoke this. */
    data object SaveAfterCredential : FamilyMemberIntent
    data object Cancel : FamilyMemberIntent
}

/**
 * New-member onboarding never writes on screen entry or form input. Activity
 * stages a validated request and must obtain a fresh OS credential confirmation.
 *
 * Actual primary-owner choice and name validation are repeated atomically inside
 * VaultRepository.registerFamilyMember, guarded by requireRecentApproval().
 */
@HiltViewModel
class FamilyMemberViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FamilyMemberUiState>(FamilyMemberUiState.Locked)
    val state: StateFlow<FamilyMemberUiState> = mutableState

    private var operation: Job? = null
    private var generation = 0L

    fun onIntent(intent: FamilyMemberIntent) {
        when (intent) {
            FamilyMemberIntent.Refresh -> refresh()
            is FamilyMemberIntent.Stage -> stage(intent)
            FamilyMemberIntent.SaveAfterCredential -> saveAfterCredential()
            FamilyMemberIntent.Cancel -> clearSensitiveState()
        }
    }

    private fun refresh() {
        operation?.cancel()
        val current = ++generation
        if (!session.isUnlocked()) {
            mutableState.value = FamilyMemberUiState.Locked
            return
        }
        mutableState.value = FamilyMemberUiState.Loading
        operation = viewModelScope.launch {
            try {
                val members = vault.members()
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = FamilyMemberUiState.Ready(members)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) {
                    mutableState.value = FamilyMemberUiState.Error(
                        "Could not read family members. Reopen the encrypted vault and retry.",
                    )
                }
            }
        }
    }

    private fun stage(intent: FamilyMemberIntent.Stage) {
        val ready = mutableState.value as? FamilyMemberUiState.Ready ?: return
        if (!session.isUnlocked()) {
            clearSensitiveState()
            return
        }
        try {
            val draft = FamilyMemberOnboarding.create(
                existing = ready.members,
                displayName = intent.displayName,
                relationship = intent.relationship,
                id = OwnerId(UUID.randomUUID().toString()),
            )
            mutableState.value = FamilyMemberUiState.AwaitingCredential(
                draft.displayName, draft.relationship,
            )
        } catch (_: IllegalArgumentException) {
            mutableState.value = ready.copy(
                validationMessage = "Use a distinct name (1–100 characters) and a valid relationship.",
            )
        }
    }

    private fun saveAfterCredential() {
        val pending = mutableState.value as? FamilyMemberUiState.AwaitingCredential ?: return
        if (!session.canCommitFinancialChanges()) {
            clearSensitiveState()
            return
        }
        operation?.cancel()
        val current = ++generation
        mutableState.value = FamilyMemberUiState.Saving
        operation = viewModelScope.launch {
            try {
                val created = vault.registerFamilyMember(pending.name, pending.relationship)
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = FamilyMemberUiState.Saved(created)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) {
                    // Never display SQL errors, names or security exception internals.
                    // Force a reload so concurrent family changes are not overwritten.
                    mutableState.value = FamilyMemberUiState.Error(
                        "The member was not confirmed. Reopen family setup to check the latest vault.",
                    )
                }
            }
        }
    }

    fun clearSensitiveState() {
        ++generation
        operation?.cancel()
        operation = null
        mutableState.value = FamilyMemberUiState.Locked
    }

    override fun onCleared() {
        clearSensitiveState()
        super.onCleared()
    }
}
