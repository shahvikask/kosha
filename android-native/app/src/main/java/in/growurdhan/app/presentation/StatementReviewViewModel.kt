package `in`.growurdhan.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.SnapshotResult
import `in`.growurdhan.domain.finance.ReviewedStatement
import `in`.growurdhan.domain.finance.StatementLine
import `in`.growurdhan.domain.vault.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface StatementReviewUiState {
    data object Empty : StatementReviewUiState
    data class SelectOwner(val members: List<FamilyMember>) : StatementReviewUiState
    data object Working : StatementReviewUiState
    data class Reviewing(val receipt: ReviewedStatement, val warning: String? = null) : StatementReviewUiState
    data class Completed(val result: SnapshotResult) : StatementReviewUiState
    data class Error(val message: String) : StatementReviewUiState
}

sealed interface StatementReviewIntent {
    /** Needs new device credential. Activity intercepts; never approve directly. */
    data object LoadOwners : StatementReviewIntent
    data class PickPdf(val ownerId: OwnerId) : StatementReviewIntent
    data object RequestApproval : StatementReviewIntent
    /** Only Activity's successful system credential callback can request this. */
    data object CommitAfterCredential : StatementReviewIntent
    data object Cancel : StatementReviewIntent
}

/**
 * Memory-only bridge from future, independently validated CASPARSER output
 * to the native review/consent screen. There is deliberately NO text or SHA
 * entry on the public UI until the binary-PDF provenance adapter is implemented.
 */
@HiltViewModel
class StatementReviewViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<StatementReviewUiState>(StatementReviewUiState.Empty)
    val state: StateFlow<StatementReviewUiState> = mutableState

    private var operation: Job? = null
    private var generation: Long = 0

    /** Not wired to the UI. Future parser adapter must validate PDF hash/account/owner first. */
    fun prepareFromValidatedParser(
        snapshotId: String,
        ownerId: OwnerId,
        sourceSha256: String,
        lines: List<StatementLine>,
    ) {
        if (!session.isUnlocked()) {
            mutableState.value = StatementReviewUiState.Error("Unlock the vault before reviewing a statement.")
            return
        }
        operation?.cancel()
        val current = ++generation
        // A copy removes the caller's ability to mutate the list after staging.
        val inputs = lines.toList()
        mutableState.value = StatementReviewUiState.Working
        operation = viewModelScope.launch {
            try {
                val receipt = vault.prepareStatementReview(snapshotId, ownerId, sourceSha256, inputs)
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = StatementReviewUiState.Reviewing(receipt)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (current == generation) mutableState.value = StatementReviewUiState.Error(
                    "Statement review could not be prepared. No financial changes were written.",
                )
            }
        }
    }

    fun onIntent(intent: StatementReviewIntent) {
        when (intent) {
            StatementReviewIntent.Cancel -> clearSensitiveState()
            StatementReviewIntent.LoadOwners -> loadOwners()
            is StatementReviewIntent.PickPdf -> Unit // Activity owns the Android document chooser.
            StatementReviewIntent.RequestApproval -> Unit // Activity must obtain fresh device credential.
            StatementReviewIntent.CommitAfterCredential -> commitAfterCredential()
        }
    }

    private fun loadOwners() {
        if (!session.isUnlocked()) {
            mutableState.value = StatementReviewUiState.Error(
                "Unlock the encrypted vault before selecting a PDF."
            )
            return
        }
        operation?.cancel()
        val current = ++generation
        mutableState.value = StatementReviewUiState.Working
        operation = viewModelScope.launch {
            try {
                val members = vault.members()
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = if (members.isEmpty()) StatementReviewUiState.Error(
                        "Add a family member to the vault before reviewing a statement."
                    ) else StatementReviewUiState.SelectOwner(members)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) mutableState.value = StatementReviewUiState.Error(
                    "Unable to open the family list. Unlock the vault and try again."
                )
            }
        }
    }

    fun reportParserFailure() {
        ++generation
        operation?.cancel()
        operation = null
        mutableState.value = StatementReviewUiState.Error(
            "This PDF could not be verified on-device. Nothing was imported. " +
                "Try an unencrypted CAS PDF on a supported ARM64 build, or review account mapping."
        )
    }

    private fun commitAfterCredential() {
        val reviewing = mutableState.value as? StatementReviewUiState.Reviewing ?: return
        if (!session.canCommitFinancialChanges()) {
            mutableState.value = reviewing.copy(
                warning = "Reconfirm your secure device credential before approving.",
            )
            return
        }
        if (reviewing.receipt.plan.lines.any {
                it.outcome == `in`.growurdhan.domain.finance.ReconciliationOutcome.CONFLICT
            }
        ) {
            mutableState.value = reviewing.copy(warning = "Resolve statement conflicts before approval.")
            return
        }
        operation?.cancel()
        val current = ++generation
        mutableState.value = StatementReviewUiState.Working
        operation = viewModelScope.launch {
            try {
                val result = vault.commitReviewedStatement(reviewing.receipt)
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = StatementReviewUiState.Completed(result)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (current == generation) {
                    // Fail closed. The in-memory receipt may now be stale; require review again.
                    mutableState.value = StatementReviewUiState.Error(
                        "Statement approval was not confirmed. Re-review against the latest book.",
                    )
                }
            }
        }
    }

    /** Background, cancel, logout or failed authentication: no SavedStateHandle. */
    fun clearSensitiveState() {
        ++generation
        operation?.cancel()
        operation = null
        mutableState.value = StatementReviewUiState.Empty
    }

    override fun onCleared() {
        clearSensitiveState()
        super.onCleared()
    }
}
