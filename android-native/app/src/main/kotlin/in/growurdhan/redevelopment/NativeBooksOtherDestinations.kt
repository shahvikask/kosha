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

@Composable
internal fun NativeBooksDocumentsDestination(state: NativeBooksUiState,
    data: NativeBooksRepository.Snapshot) {
    val email = state.email
    val books = state.books
    val imports = state.imports
    val payouts = state.payouts
    val annualCredits = state.annualCredits
    val diagnostics = state.diagnostics
    val casPdf = state.casPdf
    val settings = state.settings
    val privacyConsent = state.privacyConsent
    val onLock = state.onLock
    val onReplaceRecovery = state.onReplaceRecovery
    val onAppearanceChanged = state.onAppearanceChanged
    val onSelectPdf = state.onSelectPdf
    val onDiscardPdf = state.onDiscardPdf
    val onSelectCsv = state.onSelectCsv
    val onDiscardCsv = state.onDiscardCsv
    val pendingPdf = state.pendingPdf
    val pendingPdfFilename = state.pendingPdfFilename
    val pendingCsv = state.pendingCsv
    val pendingCsvFilename = state.pendingCsvFilename
    val scope = state.scope
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
    val active = data.people.filter { it.active }
    val lensPeople = active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
    val lensCustody = data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
    suspend fun refresh() = state.refresh()
    fun persist(task: () -> Unit) = state.persist(task)

                        NativeHouseholdDocumentAccessGate(ownerFilter) {
                            NativeCasPdfCard(email, imports, casPdf, pendingPdf,
                                pendingPdfFilename, onSelectPdf, onDiscardPdf) {
                                scope.launch {
                                    try { refresh() } catch (_: Exception) {
                                        error = "Cannot refresh encrypted document history"
                                    }
                                }
                            }
                            NativeCsvImportCard(email, imports, pendingCsv, pendingCsvFilename,
                                onSelectCsv, onDiscardCsv) {
                                scope.launch {
                                    try { refresh() } catch (_: Exception) {
                                        error = "Could not refresh encrypted import history"
                                    }
                                }
                            }
                            Text("Statement and import history", style = MaterialTheme.typography.titleLarge)
                            Text("Records below are read from the encrypted database, not from an email server.")
                            if (data.imports.isEmpty()) Text("No statement imports recorded.")
                            data.imports.forEach { batch ->
                                HorizontalDivider()
                                Text("${batch.filename} • ${batch.documentKind}",
                                    style = MaterialTheme.typography.titleMedium)
                                Text("Statement date: ${batch.statementDate ?: "not provided"}")
                                Text("Approved new rows: ${batch.newRows} • duplicates: ${batch.duplicateRows}" +
                                    " • conflicts: ${batch.conflicts}")
                                Text("Imported ${batch.committedAt}")
                            }
                            if (data.documents.isEmpty()) Text("No source-document fingerprints recorded.")
                            data.documents.forEach { document ->
                                Text("${document.filename} • ${document.kind} • ${document.byteLength} bytes")
                                Text("Source SHA-256 ${document.sha256.take(16)}…",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        Text("Original PDF bytes are not represented by these fingerprint records.")
                        }

}

@Composable
internal fun NativeBooksSettingsDestination(state: NativeBooksUiState,
    data: NativeBooksRepository.Snapshot) {
    val email = state.email
    val books = state.books
    val imports = state.imports
    val payouts = state.payouts
    val annualCredits = state.annualCredits
    val diagnostics = state.diagnostics
    val casPdf = state.casPdf
    val settings = state.settings
    val privacyConsent = state.privacyConsent
    val onLock = state.onLock
    val onReplaceRecovery = state.onReplaceRecovery
    val onAppearanceChanged = state.onAppearanceChanged
    val onSelectPdf = state.onSelectPdf
    val onDiscardPdf = state.onDiscardPdf
    val onSelectCsv = state.onSelectCsv
    val onDiscardCsv = state.onDiscardCsv
    val pendingPdf = state.pendingPdf
    val pendingPdfFilename = state.pendingPdfFilename
    val pendingCsv = state.pendingCsv
    val pendingCsvFilename = state.pendingCsvFilename
    val scope = state.scope
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
    val active = data.people.filter { it.active }
    val lensPeople = active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
    val lensCustody = data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
    suspend fun refresh() = state.refresh()
    fun persist(task: () -> Unit) = state.persist(task)

                        if (preferencesLoaded) {
                            NativeUserSettingsCard(
                                preferences = preferences, enabled = !preferencesBusy,
                                ibja = ibjaInput,
                                onRecordIbja = { date, rate ->
                                    if (!preferencesBusy) {
                                        preferencesBusy = true
                                        preferencesError = ""
                                        scope.launch {
                                            try {
                                                ibjaInput = withContext(Dispatchers.IO) {
                                                    requireNotNull(settings).saveIbja(email, date, rate)
                                                }
                                            } catch (_: Exception) {
                                                preferencesError = "IBJA reference not saved. Check " +
                                                    "date, positive rate, or unlock the vault."
                                            } finally { preferencesBusy = false }
                                        }
                                    }
                                },
                                onClearIbja = {
                                    if (!preferencesBusy) {
                                        preferencesBusy = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    requireNotNull(settings).clearIbja(email)
                                                }
                                                ibjaInput = null
                                                preferencesError = "Owner-entered IBJA reference deleted."
                                            } catch (_: Exception) {
                                                preferencesError = "Cannot delete IBJA reference; unlock again."
                                            } finally { preferencesBusy = false }
                                        }
                                    }
                                },
                                onLock = onLock,
                                onAccountAccess = onReplaceRecovery,
                                onOpenFamily = { state.onNavigate("Family") },
                                onOpenAccounts = { state.onNavigate("Accounts") }
                            ) { next ->
                                if (!preferencesBusy) {
                                    preferencesBusy = true
                                    preferencesError = ""
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { requireNotNull(settings).save(email, next) }
                                            if (next.portfolioSort != preferences.portfolioSort)
                                                holdingSort = next.portfolioSort.holdingSort
                                            if (next.archiveDefault != preferences.archiveDefault)
                                                holdingStatusFilter = next.archiveDefault.holdingStatus
                                            preferences = next
                                            onAppearanceChanged(next.appearance)
                                        } catch (_: Exception) {
                                            preferencesError = "Setting not saved; unlock the vault again."
                                        } finally { preferencesBusy = false }
                                    }
                                }
                            }
                        } else Text("Preferences require an unlocked encrypted vault.")
                        if (preferencesError.isNotEmpty()) Text(preferencesError)
                        if (ownerFilter.isNotEmpty()) Text("Device settings apply to the whole household, regardless of the selected member lens.")
                        NativeAmfiNavCard(email, books) {
                            scope.launch {
                                try { refresh() } catch (_: Exception) {
                                    error = "Cannot refresh encrypted NAV display"
                                }
                            }
                        }
                        NativeGmailOptInCard(email, books, data.people)
                        NativeBillingStatusNotice()
                        if (privacyConsent != null) NativePrivacyConsentCard(email, privacyConsent)
                        NativePrivateDiagnosticsSettings(email, diagnostics)

}

@Composable
internal fun NativeBooksRecordsDestination(state: NativeBooksUiState,
    data: NativeBooksRepository.Snapshot) {
    val email = state.email
    val books = state.books
    val imports = state.imports
    val payouts = state.payouts
    val annualCredits = state.annualCredits
    val diagnostics = state.diagnostics
    val casPdf = state.casPdf
    val settings = state.settings
    val privacyConsent = state.privacyConsent
    val onLock = state.onLock
    val onReplaceRecovery = state.onReplaceRecovery
    val onAppearanceChanged = state.onAppearanceChanged
    val onSelectPdf = state.onSelectPdf
    val onDiscardPdf = state.onDiscardPdf
    val onSelectCsv = state.onSelectCsv
    val onDiscardCsv = state.onDiscardCsv
    val pendingPdf = state.pendingPdf
    val pendingPdfFilename = state.pendingPdfFilename
    val pendingCsv = state.pendingCsv
    val pendingCsvFilename = state.pendingCsvFilename
    val scope = state.scope
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
    val active = data.people.filter { it.active }
    val lensPeople = active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
    val lensCustody = data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
    suspend fun refresh() = state.refresh()
    fun persist(task: () -> Unit) = state.persist(task)

    key(ownerFilter) {
                        // User-selected holding/correction/payment form state cannot
                        // survive a change from one member's lens to another.
                        Text("Money events and manual prices", style = MaterialTheme.typography.titleLarge)
                        EventForm(data.holdings.filter { ownerFilter.isEmpty() ||
                            it.personId == ownerFilter }, busy,
                            historyRevision = ledgerRevision,
                            loadHistory = { id -> books.history(email, id) },
                            add = { form -> persist { books.appendUserEvent(email, form) } },
                            price = { id, value, day ->
                                persist { books.recordManualPrice(email, id, value, day) }
                            })
                        Text("Statement adjustments cannot be created as manual buys or sells.",
                            style = MaterialTheme.typography.bodySmall)
                        NativeTaxWorkpaperCard(email, books, data.holdings.filter {
                            ownerFilter.isEmpty() || it.personId == ownerFilter
                        })
                        NativeAnnualInterestCreditCard(email, annualCredits,
                            data.holdings.filter { ownerFilter.isEmpty() ||
                                it.personId == ownerFilter }, busy) {
                            scope.launch { try { refresh() } catch (_: Exception) {
                                error = "Cannot refresh annual interest records"
                            } }
                        }
                        NativePpfSsyPassbookCard(email, books, data.holdings.filter {
                            ownerFilter.isEmpty() || it.personId == ownerFilter
                        }, busy)
                        NativeInterestPayoutForm(email, payouts, data.holdings.filter {
                            (it.category == "FIXED_INCOME" || it.category == "GOLD_SGB") &&
                                (ownerFilter.isEmpty() || it.personId == ownerFilter)
                        }, busy) { scope.launch { try { refresh() } catch (_: Exception) {
                            error = "Cannot refresh reviewed interest history" } } }
                        NativeEmiForm(data.holdings.filter {
                            it.category == "LIABILITY" &&
                                (ownerFilter.isEmpty() || it.personId == ownerFilter)
                        }, busy,
                            onSettings = { id, status, monthly, next, remaining ->
                                persist { books.updateEmiSchedule(email, id, status,
                                    monthly, next, remaining) }
                            },
                            onCatchUp = { id, asOf ->
                                persist { books.catchUpEmi(email, id, asOf) }
                            })
    }

}
