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
internal fun NativeBooksHomeDestination(state: NativeBooksUiState,
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

                        val selected = data.holdings.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                        val warnings = selected.count { it.unknownCost || it.needsReview || it.priceDate == null }
                        Text("${selected.size} holdings • ${lensPeople.size} active ${if (lensPeople.size == 1) "member" else "members"} in view",
                            style = MaterialTheme.typography.titleLarge)
                        Text("$warnings holding(s) need review or cost/price confirmation")
                        val health = NativeBooksHomeHealth.summarize(
                            data.holdings, ownerFilter, LocalDate.now())
                        Text("Books health: ${health.active} active, ${health.archived} archived; " +
                            "${health.unknownCost} asset(s) with unknown acquisition cost; " +
                            "${health.needsReview} needing explicit review.",
                            modifier = Modifier.testTag("m29-home-health"))
                        Text("Dated price coverage: ${health.missingDatedPrice} missing, " +
                            "${health.staleDatedPrice} at least 45 days old, " +
                            "${health.invalidOrFuturePrice} invalid or future. " +
                            "No absent price is used as a live quote.",
                            modifier = Modifier.testTag("m29-home-price-health"))
                        Text("Asset categories in current owner view: " +
                            (health.categories.joinToString { "${it.first} ${it.second}" }
                                .ifEmpty { "None" }),
                            modifier = Modifier.testTag("m29-home-categories"))
                        Text("Home displays book records. No market value is calculated from an absent quote.")
                        Text("Home preview: up to ${preferences.homePreview.maxRows} owner-scoped holding(s)",
                            modifier = Modifier.testTag("m31-home-preview-limit"))
                        selected.take(preferences.homePreview.maxRows).forEach { h ->
                            Text("${h.name}: ${NativeBooksRepository.formatMinor(h.quantityScaled, 8)} units " +
                                "• ${h.personName}")
                        }
                        key(email, ownerFilter) {
                            NativeRecordedFinanceCard(email, books, ownerFilter, data.holdings)
                            NativeE09ManualCashCard(email, books, ownerFilter)
                            NativeHybridExposureCard(email, books, data.holdings, ownerFilter)
                        }

}

@Composable
internal fun NativeBooksFamilyDestination(state: NativeBooksUiState,
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

                        Text("Family members", style = MaterialTheme.typography.titleLarge)
                        active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
                            .forEach { m ->
                            Text("${m.name} • ${m.relation} • ${m.kind}" +
                                (if (m.isPrimary) " (Primary)" else "") +
                                (m.maskedPan?.let { " • $it" } ?: ""))
                        }
                        key(ownerFilter) {
                            NativeEmergencyDossierCard(data,
                                selectedOwnerId = ownerFilter.ifEmpty { null })
                        }
                        if (ownerFilter.isEmpty()) {
                            PersonForm(active, busy) { form ->
                                persist { books.createPerson(email, form) }
                            }
                        } else Text("Switch to Whole household to add a family member.")
                        key(ownerFilter) {
                            PersonEditForm(lensPeople, busy,
                                update = { id, form -> persist { books.updatePerson(email, id, form) } },
                                archive = { id -> persist { books.archivePerson(email, id) } })
                        }

}

@Composable
internal fun NativeBooksAccountsDestination(state: NativeBooksUiState,
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

                        Text("Custody accounts", style = MaterialTheme.typography.titleLarge)
                        data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                            .forEach { a ->
                                val name = active.firstOrNull { it.id == a.personId }?.name ?: "Archived member"
                                Text("${a.custodian} • ${a.type} • $name" +
                                    (a.maskedReference?.let { " • $it" } ?: ""))
                            }
                        key(ownerFilter) {
                            CustodyForm(lensPeople, busy) { form ->
                                if (ownerFilter.isEmpty() || form.personId == ownerFilter)
                                    persist { books.createCustody(email, form) }
                            }
                            CustodyEditForm(lensCustody, busy,
                                update = { id, form -> persist { books.updateCustody(email, id, form) } },
                                close = { a -> persist {
                                    books.closeCustody(email, a.personId, a.id, LocalDate.now().toString())
                                } })
                        }

}

@Composable
internal fun NativeBooksGoalsDestination(state: NativeBooksUiState,
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

                        Text("Family goals", style = MaterialTheme.typography.titleLarge)
                        key(email, ownerFilter) {
                            NativeE09GoalProgressCard(email, books, ownerFilter, data.holdings)
                        }
                        data.goals.filter { ownerFilter.isEmpty() || it.personId == null ||
                            it.personId == ownerFilter }.forEach { g ->
                            Text("${g.name} • ${g.targetMinor?.let {
                                "₹" + NativeBooksRepository.formatMinor(it, 4)
                            } ?: "Target not recorded"} • ${g.targetDate.orEmpty()}")
                        }
                        key(ownerFilter) {
                            GoalForm(lensPeople, busy, ownerFilter) { id, name, minor, day, priority ->
                                if (ownerFilter.isEmpty() || id == ownerFilter)
                                    persist { books.createGoal(email, id, name, minor, day, priority) }
                            }
                            GoalEditForm(data.goals.filter { ownerFilter.isEmpty() ||
                                it.personId == ownerFilter }, busy,
                                update = { goal, name, amount, day, priority -> persist {
                                    books.updateGoal(email, goal.id, goal.personId, name, amount, day, priority)
                                } },
                                remove = { id -> persist { books.removeUnlinkedGoal(email, id) } })
                        }

}
