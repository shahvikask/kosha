package `in`.growurdhan.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import `in`.growurdhan.app.presentation.AppViewModel
import `in`.growurdhan.app.presentation.GrowUrDhanShell
import `in`.growurdhan.app.presentation.StatementReviewIntent
import `in`.growurdhan.app.presentation.StatementReviewUiState
import `in`.growurdhan.app.presentation.StatementReviewViewModel
import `in`.growurdhan.app.presentation.VaultIntent
import `in`.growurdhan.app.presentation.VaultUiState
import `in`.growurdhan.app.presentation.VaultSessionGate
import `in`.growurdhan.app.presentation.VaultViewModel
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import javax.inject.Inject

/**
 * A secure-device-credential gate for the development UI, not a GrowUrDhan
 * password/biometric vault-login implementation. No credential is collected
 * by the app. This Activity never automatically opens or creates the vault.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionGate: VaultSessionGate
    private val viewModel: AppViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()
    private val reviewViewModel: StatementReviewViewModel by viewModels()
    private enum class CredentialAction { OPEN_VAULT, APPROVE_REVIEW }
    private var pendingCredential: CredentialAction? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutCheck = object : Runnable {
        override fun run() {
            if (pendingCredential == null && vaultViewModel.state.value is VaultUiState.Ready &&
                !sessionGate.isUnlocked()
            ) {
                lockSensitiveUi()
            }
            timeoutHandler.postDelayed(this, 5_000L)
        }
    }

    private val credentialResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pendingCredential
        pendingCredential = null
        if (result.resultCode == Activity.RESULT_OK && action != null &&
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        ) {
            sessionGate.grantDeviceCredential()
            when (action) {
                CredentialAction.OPEN_VAULT -> vaultViewModel.onIntent(VaultIntent.OpenOrCreate)
                CredentialAction.APPROVE_REVIEW ->
                    reviewViewModel.onIntent(StatementReviewIntent.CommitAfterCredential)
            }
        } else {
            // Device credential cancelled, removed, or otherwise not verified.
            lockSensitiveUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never allow financial rows or receipt details in OS screenshots/recents.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val vaultState by vaultViewModel.state.collectAsStateWithLifecycle()
            val reviewState by reviewViewModel.state.collectAsStateWithLifecycle()
            GrowUrDhanTheme(state.appearance) {
                GrowUrDhanShell(
                    state = state,
                    onIntent = viewModel::onIntent,
                    vaultState = vaultState,
                    onVaultIntent = ::handleVaultIntent,
                    reviewState = reviewState,
                    onReviewIntent = ::handleReviewIntent,
                )
            }
        }
    }

    private fun handleVaultIntent(intent: VaultIntent) {
        if (intent == VaultIntent.OpenOrCreate) {
            requestCredential(CredentialAction.OPEN_VAULT)
        } else {
            if (intent == VaultIntent.Close) reviewViewModel.clearSensitiveState()
            vaultViewModel.onIntent(intent)
        }
    }

    private fun handleReviewIntent(intent: StatementReviewIntent) {
        when (intent) {
            StatementReviewIntent.RequestApproval -> {
                if (reviewViewModel.state.value !is StatementReviewUiState.Reviewing) return
                if (!sessionGate.isUnlocked()) {
                    lockSensitiveUi()
                } else {
                    requestCredential(CredentialAction.APPROVE_REVIEW)
                }
            }
            StatementReviewIntent.Cancel -> reviewViewModel.clearSensitiveState()
            StatementReviewIntent.CommitAfterCredential -> Unit // never accept from UI
        }
    }

    private fun requestCredential(action: CredentialAction) {
        if (pendingCredential != null) return
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!manager.isDeviceSecure) {
            if (action == CredentialAction.OPEN_VAULT) {
                vaultViewModel.onIntent(VaultIntent.CredentialUnavailable)
            } else {
                reviewViewModel.onIntent(StatementReviewIntent.Cancel)
            }
            return
        }
        val title = if (action == CredentialAction.OPEN_VAULT)
            "Unlock GrowUrDhan" else "Approve financial statement"
        val explanation = if (action == CredentialAction.OPEN_VAULT)
            "Confirm your device screen lock before opening the encrypted vault."
        else "Confirm your device screen lock before committing these financial changes."
        val challenge = manager.createConfirmDeviceCredentialIntent(title, explanation)
        if (challenge == null) {
            lockSensitiveUi()
            return
        }
        pendingCredential = action
        try {
            credentialResult.launch(challenge)
        } catch (_: Exception) {
            pendingCredential = null
            lockSensitiveUi()
        }
    }

    private fun lockSensitiveUi() {
        sessionGate.lock()
        reviewViewModel.clearSensitiveState()
        vaultViewModel.onIntent(VaultIntent.Close)
    }

    override fun onStart() {
        super.onStart()
        timeoutHandler.removeCallbacks(timeoutCheck)
        timeoutHandler.postDelayed(timeoutCheck, 5_000L)
    }

    override fun onStop() {
        timeoutHandler.removeCallbacks(timeoutCheck)
        super.onStop()
        // Android's own credential confirmation activity may temporarily stop us.
        // A cancelled challenge locks; success must come from RESULT_OK only.
        if (pendingCredential == null) lockSensitiveUi()
    }
}
