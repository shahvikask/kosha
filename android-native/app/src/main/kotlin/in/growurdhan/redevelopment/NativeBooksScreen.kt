package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.model.NativeAssetTaxonomy
import `in`.growurdhan.model.NativeInterestPayoutPlanner
import `in`.growurdhan.model.NativeOwnershipEconomicShare
import `in`.growurdhan.model.NativeTaxLotTrace
import `in`.growurdhan.model.NativeTradeCashReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Real SQLCipher-backed owner-lens navigation. No example balances or sample records.
 * Financial values are rendered only while the parent Activity has an admitted session.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeBooksScreen(email: String, books: NativeBooksRepository,
    imports: NativeStatementImportRepository,
    payouts: NativeInterestPayoutRepository,
    annualCredits: NativeAnnualInterestCreditRepository,
    diagnostics: NativePrivateDiagnosticsRepository, casPdf: NativeCasPdfEngine,
    pendingPdf: ByteArray?, pendingPdfFilename: String,
    onSelectPdf: () -> Unit, onDiscardPdf: () -> Unit,
    pendingCsv: ByteArray?, pendingCsvFilename: String,
    onSelectCsv: () -> Unit, onDiscardCsv: () -> Unit,
    onLock: () -> Unit, onReplaceRecovery: () -> Unit,
    settings: NativeUserSettingsRepository? = null,
    privacyConsent: NativePrivacyConsentRepository? = null,
    onAppearanceChanged: (NativeUserSettingsRepository.Appearance) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var tab by androidx.compose.runtime.saveable.rememberSaveable(email) {
        mutableStateOf("Home")
    }
    val state = remember(email, books) {
        NativeBooksUiState(email, books, imports, payouts, annualCredits,
            diagnostics, casPdf, settings, privacyConsent,
            { tab = "Home"; onLock() },
            onReplaceRecovery, onAppearanceChanged, scope)
    }
    state.pendingPdf = pendingPdf
    state.pendingPdfFilename = pendingPdfFilename
    state.onSelectPdf = onSelectPdf
    state.onDiscardPdf = onDiscardPdf
    state.pendingCsv = pendingCsv
    state.pendingCsvFilename = pendingCsvFilename
    state.onSelectCsv = onSelectCsv
    state.onDiscardCsv = onDiscardCsv
    state.onNavigate = { requested -> tab = requested }
    var snapshot by state::snapshot
    var busy by state::busy
    var ledgerRevision by state::ledgerRevision
    var error by state::error
    var exchangeReceipt by state::exchangeReceipt
    var preferences by state::preferences
    var preferencesLoaded by state::preferencesLoaded
    var preferencesBusy by state::preferencesBusy
    var preferencesError by state::preferencesError
    var ibjaInput by state::ibjaInput
    var ownerFilter by state::ownerFilter
    var categoryFilter by state::categoryFilter
    var holdingAccountFilter by state::holdingAccountFilter
    var holdingStatusFilter by state::holdingStatusFilter
    var holdingSort by state::holdingSort
    var search by state::search
    var historyId by state::historyId
    var history by state::history
    var xirrResults by state::xirrResults
    suspend fun refresh() = state.refresh()
    androidx.activity.compose.BackHandler(enabled = tab != "Home") {
        tab = when (tab) {
            "Accounts", "Settings" -> "Family"
            "Records" -> "Documents"
            else -> "Home"
        }
    }
    LaunchedEffect(ownerFilter) {
        // Never show a previous member's audit history or calculated return
        // after switching the active lens.
        historyId = ""
        history = emptyList()
        xirrResults = emptyMap()
        exchangeReceipt = ""
        // A member switch must revoke even the account filter selection.
        holdingAccountFilter = ""
    }
    LaunchedEffect(email) {
        xirrResults = emptyMap()
        preferencesLoaded = false
        try {
            preferences = withContext(Dispatchers.IO) {
                settings?.read(email) ?: NativeUserSettingsRepository.Preferences()
            }
            preferencesLoaded = settings != null
            holdingSort = preferences.portfolioSort.holdingSort
            holdingStatusFilter = preferences.archiveDefault.holdingStatus
            ibjaInput = withContext(Dispatchers.IO) { settings?.readIbja(email) }
        } catch (_: Exception) {
            preferencesError = "Encrypted settings unavailable. Lock and unlock again."
        }
        try { refresh() } catch (_: Exception) {
            error = "Unable to open encrypted books. Lock and verify this device again."
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NativeBooksNavigation(NativeBooksMainDestination(tab)) { destination ->
                tab = when (destination) {
                    "Statements" -> "Documents"
                    "Insights" -> "Goals"
                    else -> destination
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GUDBrandLockup()
            Text("Private family books", style = MaterialTheme.typography.headlineSmall)
            Text("On this device only. Values are recorded holdings, not live market quotes.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { tab = "Home"; onLock() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Lock vault") }
                OutlinedButton(onClick = onReplaceRecovery, modifier = Modifier.heightIn(min = 48.dp)) { Text("Recovery code") }
            }
            val data = snapshot
            if (data == null) {
                Text(if (error.isEmpty()) "Opening encrypted records…" else error)
            } else {
                val active = data.people.filter { it.active }
                val lensPeople = active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
                val lensCustody = data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                OwnerPicker(active, ownerFilter) { ownerFilter = it }
                NativeOwnerLensStatus(
                    if (ownerFilter.isEmpty()) null
                    else active.firstOrNull { it.id == ownerFilter }?.name
                        ?: "Selected member unavailable"
                )
                NativeBooksSubNavigation(tab) { tab = it }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                when (tab) {
                    "Home" -> NativeBooksHomeDestination(state, data)
                    "Family" -> NativeBooksFamilyDestination(state, data)
                    "Accounts" -> NativeBooksAccountsDestination(state, data)
                    "Goals" -> NativeBooksGoalsDestination(state, data)
                    "Holdings" -> NativeBooksHoldingsDestination(state, data)
                    "Documents" -> NativeBooksDocumentsDestination(state, data)
                    "Settings" -> NativeBooksSettingsDestination(state, data)
                    "Records" -> NativeBooksRecordsDestination(state, data)
                    else -> NativeBooksHomeDestination(state, data)
                }
            }
        }
    }
}
