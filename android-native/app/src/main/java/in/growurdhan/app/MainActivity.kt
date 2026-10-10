package `in`.growurdhan.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import `in`.growurdhan.app.cas.NativeCasPdfReader
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.finance.toFinancialHolding
import `in`.growurdhan.domain.vault.VaultRepository
import `in`.growurdhan.app.presentation.FamilyMemberIntent
import `in`.growurdhan.app.presentation.FamilyMemberUiState
import `in`.growurdhan.app.presentation.FamilyMemberViewModel
import `in`.growurdhan.app.presentation.PortableRecoveryMode
import `in`.growurdhan.app.presentation.PortableRecoveryUiState
import `in`.growurdhan.app.presentation.PortableRecoveryViewModel
import `in`.growurdhan.app.presentation.screens.PortableRecoveryIntent
import `in`.growurdhan.app.presentation.CustodyHoldingIntent
import `in`.growurdhan.app.presentation.CustodyHoldingUiState
import `in`.growurdhan.app.presentation.CustodyHoldingViewModel
import `in`.growurdhan.app.presentation.AppViewModel
import `in`.growurdhan.app.presentation.GrowUrDhanShell
import `in`.growurdhan.app.presentation.StatementReviewIntent
import `in`.growurdhan.app.presentation.StatementReviewUiState
import `in`.growurdhan.app.presentation.StatementReviewViewModel
import `in`.growurdhan.app.presentation.VaultIntent
import `in`.growurdhan.app.presentation.VaultUiState
import `in`.growurdhan.app.presentation.VaultSessionGate
import `in`.growurdhan.app.presentation.VaultViewModel
import `in`.growurdhan.app.presentation.HouseholdDashboardViewModel
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * A secure-device-credential gate for the development UI, not a GrowUrDhan
 * password/biometric vault-login implementation. No credential is collected
 * by the app. This Activity never automatically opens or creates the vault.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionGate: VaultSessionGate
    @Inject lateinit var casReader: NativeCasPdfReader
    @Inject lateinit var repository: VaultRepository
    private val viewModel: AppViewModel by viewModels()
    private val vaultViewModel: VaultViewModel by viewModels()
    private val dashboardViewModel: HouseholdDashboardViewModel by viewModels()
    private val reviewViewModel: StatementReviewViewModel by viewModels()
    private val familyViewModel: FamilyMemberViewModel by viewModels()
    private val custodyViewModel: CustodyHoldingViewModel by viewModels()
    private val recoveryViewModel: PortableRecoveryViewModel by viewModels()
    private enum class CredentialAction {
        OPEN_VAULT, APPROVE_REVIEW, IMPORT_PDF, ADD_FAMILY_MEMBER, REGISTER_CUSTODY, PORTABLE_BACKUP,
    }
    private var pendingCredential: CredentialAction? = null
    private var pdfPickerOpen: Boolean = false
    private var backupPickerOpen: Boolean = false
    private var backupMode: PortableRecoveryMode? = null
    private var backupUri: Uri? = null
    private var backupPassword: CharArray? = null
    private var pickedPdfOwner: OwnerId? = null
    private var pickedPdfPassword: CharArray? = null
    private var pickedPdfUri: Uri? = null
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
                CredentialAction.IMPORT_PDF -> parseSelectedPdfAfterCredential()
                CredentialAction.ADD_FAMILY_MEMBER ->
                    familyViewModel.onIntent(FamilyMemberIntent.SaveAfterCredential)
                CredentialAction.REGISTER_CUSTODY ->
                    custodyViewModel.onIntent(CustodyHoldingIntent.SaveAfterCredential)
                CredentialAction.PORTABLE_BACKUP -> recoverAfterCredential()
            }
        } else {
            // Device credential cancelled, removed, or otherwise not verified.
            lockSensitiveUi()
        }
    }

    // SAF document chooser grants read access only to the selected local/cloud URI.
    // No blanket READ_EXTERNAL_STORAGE or INTERNET permission is requested.
    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pdfPickerOpen = false
        if (uri == null || pickedPdfOwner == null) {
            lockSensitiveUi()
        } else {
            pickedPdfUri = uri
            // OS picker/background revokes prior session; require device credential
            // again before even reading the PDF or decrypted financial book.
            requestCredential(CredentialAction.IMPORT_PDF)
        }
    }

    // An external document picker revokes the active vault session. Only the
    // selected URI and operation kind survive; the backup password is entered
    // *after* returning to the app and before a new system credential check.
    private val backupExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        completeBackupDocumentSelection(uri, PortableRecoveryMode.EXPORT)
    }

    private val backupRestorePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        completeBackupDocumentSelection(uri, PortableRecoveryMode.RESTORE)
    }

    private fun completeBackupDocumentSelection(uri: Uri?, mode: PortableRecoveryMode) {
        backupPickerOpen = false
        if (uri == null || backupMode != mode) {
            lockSensitiveUi()
            return
        }
        backupUri = uri
        recoveryViewModel.offerPickedDocument(mode)
    }

    private fun recoverAfterCredential() {
        val selectedMode = backupMode
        val selectedUri = backupUri
        val selectedPassword = backupPassword
        backupMode = null
        backupUri = null
        backupPassword = null
        if (selectedMode == null || selectedUri == null || selectedPassword == null ||
            !sessionGate.canCommitFinancialChanges()
        ) {
            selectedPassword?.fill('\u0000')
            lockSensitiveUi()
            return
        }
        recoveryViewModel.executeAfterCredential(selectedMode, selectedUri, selectedPassword)
    }

    private fun parseSelectedPdfAfterCredential() {
        val uri = pickedPdfUri
        val owner = pickedPdfOwner
        val password = pickedPdfPassword
        pickedPdfUri = null
        pickedPdfOwner = null
        pickedPdfPassword = null
        if (uri == null || owner == null || !sessionGate.isUnlocked()) {
            lockSensitiveUi()
            return
        }
        // Closing the vault while the chooser was foregrounded is intentional.
        vaultViewModel.onIntent(VaultIntent.OpenOrCreate)
        lifecycleScope.launch {
            try {
                repository.openOrCreate()
                val book = repository.readBook()
                check(book.members.any { it.id == owner }) {
                    "Statement owner does not exist in encrypted book."
                }
                val verified = casReader.parseAndValidate(
                    uri, owner, book.holdings.map { it.toFinancialHolding() },
                    book.casIdentities,
                    password?.concatToString() ?: "",
                )
                check(sessionGate.isUnlocked()) { "Vault session expired during PDF parsing." }
                reviewViewModel.prepareFromValidatedParser(
                    "cas_${verified.sourceSha256}",
                    owner,
                    verified.sourceSha256,
                    verified.lines,
                )
            } catch (_: Exception) {
                // Source content, Python errors, file path, PAN and password
                // never cross into UI error messages or diagnostic logs.
                reviewViewModel.reportParserFailure()
            } finally {
                password?.fill('\u0000')
            }
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
            val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(vaultState) {
                if (vaultState is VaultUiState.Ready) dashboardViewModel.refresh()
                else dashboardViewModel.lock()
            }
            val reviewState by reviewViewModel.state.collectAsStateWithLifecycle()
            val familyState by familyViewModel.state.collectAsStateWithLifecycle()
            val custodyState by custodyViewModel.state.collectAsStateWithLifecycle()
            val recoveryState by recoveryViewModel.state.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(reviewState) {
                if (reviewState is StatementReviewUiState.Completed) {
                    dashboardViewModel.refresh()
                    vaultViewModel.onIntent(VaultIntent.Refresh)
                }
            }
            androidx.compose.runtime.LaunchedEffect(familyState) {
                if (familyState is FamilyMemberUiState.Saved) {
                    // A successful encrypted write updates the vault's member count.
                    vaultViewModel.onIntent(VaultIntent.Refresh)
                    dashboardViewModel.refresh()
                }
            }
            androidx.compose.runtime.LaunchedEffect(custodyState) {
                if (custodyState is CustodyHoldingUiState.Saved) {
                    vaultViewModel.onIntent(VaultIntent.Refresh)
                    dashboardViewModel.refresh()
                }
            }
            androidx.compose.runtime.LaunchedEffect(recoveryState) {
                if (recoveryState is PortableRecoveryUiState.Completed) {
                    vaultViewModel.onIntent(VaultIntent.Refresh)
                    dashboardViewModel.refresh()
                }
            }
            GrowUrDhanTheme(state.appearance) {
                GrowUrDhanShell(
                    state = state,
                    onIntent = viewModel::onIntent,
                    vaultState = vaultState,
                    dashboardState = dashboardState,
                    onVaultIntent = ::handleVaultIntent,
                    reviewState = reviewState,
                    onReviewIntent = ::handleReviewIntent,
                    familyState = familyState,
                    onFamilyIntent = ::handleFamilyIntent,
                    custodyState = custodyState,
                    onCustodyIntent = ::handleCustodyIntent,
                    recoveryState = recoveryState,
                    onRecoveryIntent = ::handleRecoveryIntent,
                )
            }
        }
    }

    private fun handleVaultIntent(intent: VaultIntent) {
        if (intent == VaultIntent.OpenOrCreate) {
            requestCredential(CredentialAction.OPEN_VAULT)
        } else {
            if (intent == VaultIntent.Close) {
                dashboardViewModel.lock()
                reviewViewModel.clearSensitiveState()
                recoveryViewModel.clearSensitiveState()
            }
            vaultViewModel.onIntent(intent)
        }
    }

    private fun handleReviewIntent(intent: StatementReviewIntent) {
        when (intent) {
            StatementReviewIntent.LoadOwners -> reviewViewModel.onIntent(intent)
            is StatementReviewIntent.PickPdf -> {
                if (!sessionGate.isUnlocked() || vaultViewModel.state.value !is VaultUiState.Ready) {
                    lockSensitiveUi()
                } else {
                    pickedPdfOwner = intent.ownerId
                    pickedPdfPassword = intent.pdfPassword.toCharArray()
                    pdfPickerOpen = true
                    try {
                        pdfPicker.launch(arrayOf("application/pdf"))
                    } catch (_: Exception) {
                        lockSensitiveUi()
                    }
                }
            }
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

    private fun handleFamilyIntent(intent: FamilyMemberIntent) {
        when (intent) {
            is FamilyMemberIntent.Stage -> {
                if (!sessionGate.isUnlocked() || vaultViewModel.state.value !is VaultUiState.Ready) {
                    lockSensitiveUi()
                    return
                }
                familyViewModel.onIntent(intent)
                // Only a validated form staged by the ViewModel may request
                // the write-capable device credential challenge.
                if (familyViewModel.state.value is FamilyMemberUiState.AwaitingCredential) {
                    requestCredential(CredentialAction.ADD_FAMILY_MEMBER)
                }
            }
            FamilyMemberIntent.SaveAfterCredential -> Unit // OS callback only, never from UI
            else -> familyViewModel.onIntent(intent)
        }
    }

    private fun handleCustodyIntent(intent: CustodyHoldingIntent) {
        when (intent) {
            is CustodyHoldingIntent.StageAccount, is CustodyHoldingIntent.StageHolding -> {
                if (!sessionGate.isUnlocked() || vaultViewModel.state.value !is VaultUiState.Ready) {
                    lockSensitiveUi()
                    return
                }
                custodyViewModel.onIntent(intent)
                if (custodyViewModel.state.value is CustodyHoldingUiState.AwaitingCredential) {
                    requestCredential(CredentialAction.REGISTER_CUSTODY)
                }
            }
            CustodyHoldingIntent.SaveAfterCredential -> Unit // OS callback only
            else -> custodyViewModel.onIntent(intent)
        }
    }

    private fun handleRecoveryIntent(intent: PortableRecoveryIntent) {
        when (intent) {
            PortableRecoveryIntent.SelectExportDestination -> {
                if (!sessionGate.isUnlocked() ||
                    vaultViewModel.state.value !is VaultUiState.Ready
                ) {
                    lockSensitiveUi()
                    return
                }
                backupMode = PortableRecoveryMode.EXPORT
                backupPickerOpen = true
                try {
                    backupExportPicker.launch("GrowUrDhan-Backup.gud")
                } catch (_: Exception) {
                    lockSensitiveUi()
                }
            }
            PortableRecoveryIntent.SelectRestoreSource -> {
                backupMode = PortableRecoveryMode.RESTORE
                backupPickerOpen = true
                try {
                    backupRestorePicker.launch(arrayOf("application/octet-stream", "*/*"))
                } catch (_: Exception) {
                    lockSensitiveUi()
                }
            }
            is PortableRecoveryIntent.ConfirmPassphrase -> {
                val mode = backupMode
                if (mode == null || backupUri == null ||
                    (recoveryViewModel.state.value as? PortableRecoveryUiState.EnterPassphrase)
                        ?.mode != mode ||
                    intent.password.length !in 12..1024 ||
                    (mode == PortableRecoveryMode.EXPORT && intent.password != intent.repeated)
                ) {
                    recoveryViewModel.failInput()
                    return
                }
                backupPassword?.fill('\u0000')
                backupPassword = intent.password.toCharArray()
                requestCredential(CredentialAction.PORTABLE_BACKUP)
            }
            PortableRecoveryIntent.Cancel -> {
                backupPassword?.fill('\u0000')
                backupPassword = null
                backupMode = null
                backupUri = null
                recoveryViewModel.clearSensitiveState()
            }
        }
    }

    private fun requestCredential(action: CredentialAction) {
        if (pendingCredential != null) return
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!manager.isDeviceSecure) {
            // Never retain a selected PDF URI or password when credentials
            // are missing/removed while returning from the document picker.
            lockSensitiveUi()
            if (action == CredentialAction.OPEN_VAULT) {
                vaultViewModel.onIntent(VaultIntent.CredentialUnavailable)
            }
            return
        }
        val title = when (action) {
            CredentialAction.OPEN_VAULT -> "Unlock GrowUrDhan"
            CredentialAction.IMPORT_PDF -> "Verify identity before reading CAS PDF"
            CredentialAction.APPROVE_REVIEW -> "Approve financial statement"
            CredentialAction.ADD_FAMILY_MEMBER -> "Add family member"
            CredentialAction.REGISTER_CUSTODY -> "Register financial account or holding"
            CredentialAction.PORTABLE_BACKUP -> "Protect portable backup and recovery"
        }
        val explanation = when (action) {
            CredentialAction.OPEN_VAULT ->
                "Confirm your device screen lock before opening the encrypted vault."
            CredentialAction.IMPORT_PDF ->
                "Confirm your device screen lock before accessing the selected PDF."
            CredentialAction.APPROVE_REVIEW ->
                "Confirm your device screen lock before committing these financial changes."
            CredentialAction.ADD_FAMILY_MEMBER ->
                "Confirm your device screen lock before saving a family member."
            CredentialAction.REGISTER_CUSTODY ->
                "Confirm your screen lock before enrolling this account or CAS holding."
            CredentialAction.PORTABLE_BACKUP ->
                "Confirm your device screen lock before exporting or restoring encrypted records."
        }
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
        dashboardViewModel.lock()
        sessionGate.lock()
        reviewViewModel.clearSensitiveState()
        familyViewModel.clearSensitiveState()
        custodyViewModel.clearSensitiveState()
        recoveryViewModel.clearSensitiveState()
        backupPassword?.fill('\u0000')
        backupPassword = null
        backupMode = null
        backupUri = null
        backupPickerOpen = false
        pickedPdfOwner = null
        pickedPdfPassword?.fill('\u0000')
        pickedPdfPassword = null
        pickedPdfUri = null
        pdfPickerOpen = false
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
        dashboardViewModel.lock()
        when {
            backupPickerOpen -> {
                // SAF destination/source selection is not a valid financial
                // session. Password is not collected before the picker.
                sessionGate.lock()
                reviewViewModel.clearSensitiveState()
                familyViewModel.clearSensitiveState()
                custodyViewModel.clearSensitiveState()
                vaultViewModel.onIntent(VaultIntent.Close)
            }
            pdfPickerOpen -> {
                // The external document chooser isn't an authenticated session.
                // Close SQLCipher and clear financial receipt state while away.
                sessionGate.lock()
                reviewViewModel.clearSensitiveState()
                familyViewModel.clearSensitiveState()
                custodyViewModel.clearSensitiveState()
                vaultViewModel.onIntent(VaultIntent.Close)
            }
            pendingCredential == null -> lockSensitiveUi()
            else -> {
                // The credential activity revokes the old session, and only its
                // RESULT_OK callback may start a new one.
                sessionGate.lock()
            }
        }
    }
    override fun onDestroy() {
        // A credential Activity can destroy/recreate its caller. No backup or
        // document password survives that process/activity boundary.
        backupPassword?.fill('\u0000')
        pickedPdfPassword?.fill('\u0000')
        backupPassword = null
        pickedPdfPassword = null
        super.onDestroy()
    }
}
