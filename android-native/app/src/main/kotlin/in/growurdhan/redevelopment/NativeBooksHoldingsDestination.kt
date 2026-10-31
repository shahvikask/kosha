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
internal fun NativeBooksHoldingsDestination(state: NativeBooksUiState,
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

                        Text("Holdings", style = MaterialTheme.typography.titleLarge)
                        if (ownerFilter.isEmpty()) {
                            NativeExchangePriceCard(busy) { exchange, archiveUrl ->
                                if (!busy) {
                                    busy = true
                                    error = ""
                                    exchangeReceipt = ""
                                    scope.launch {
                                        try {
                                            val receipt = withContext(Dispatchers.IO) {
                                                books.refreshExchangeCloses(email, exchange, archiveUrl)
                                            }
                                            refresh()
                                            exchangeReceipt = receipt.source + " dated " + receipt.date +
                                                ": " + receipt.matched + " holdings matched, " +
                                                receipt.updated + " updated. Owner-entered prices " +
                                                "take precedence. These are not live quotes."
                                        } catch (_: Exception) {
                                            error = "Official dated-close refresh rejected or unavailable. " +
                                                "Review stored prices before retrying; no automatic retry."
                                        } finally { busy = false }
                                    }
                                }
                            }
                            if (exchangeReceipt.isNotEmpty())
                                Text(exchangeReceipt, modifier = Modifier.testTag("m30-refresh-receipt"))
                        } else {
                            Text("Whole-household market close refresh requires Whole household lens.",
                                modifier = Modifier.testTag("m30-owner-scope"))
                        }
                        CategoryPicker(categoryFilter) { categoryFilter = it }
                        Choice("Account filter", listOf("" to "All custody accounts",
                            NativeHoldingListFilter.NO_ACCOUNT to "No account") +
                            lensCustody.map { it.id to it.custodian },
                            holdingAccountFilter) { holdingAccountFilter = it }
                        Choice("Archive status", listOf("All", "Active", "Archived").map { it to it },
                            holdingStatusFilter) { holdingStatusFilter = it }
                        Choice("Sort holdings", listOf("Name", "Recorded value", "Recent price",
                            "Category").map { it to it }, holdingSort) { holdingSort = it }
                        Text("Saved defaults: ${preferences.portfolioSort.holdingSort} sort, " +
                            "${preferences.archiveDefault.holdingStatus} archived filter; " +
                            "manual view choices override until next vault unlock.",
                            modifier = Modifier.testTag("m31-holdings-defaults"))
                        OutlinedTextField(search, { search = it }, label = { Text("Search holdings") },
                            modifier = Modifier.fillMaxWidth())
                        val list = NativeHoldingListFilter.apply(data.holdings, ownerFilter,
                            categoryFilter, holdingAccountFilter, holdingStatusFilter, search,
                            holdingSort)
                        Text("${list.size} holding(s) in filtered view — archived and zero-unit history retained")
                        NativeHoldingsLazyList(list) { h ->
                            HorizontalDivider()
                            Text("${h.name} • ${h.personName}", style = MaterialTheme.typography.titleMedium)
                            Text("${h.subcategory} • ${NativeBooksRepository.formatMinor(h.quantityScaled, 8)} units")
                            Text(if (h.unknownCost) "Opening cost unknown — review before using gains"
                                else "Recorded FIFO cost: ₹${NativeBooksRepository.formatMinor(h.knownCostMinor, 4)}")
                            val requireRbi = h.category == "GOLD_SGB" &&
                                h.subcategory.contains("SGB", ignoreCase = true) &&
                                preferences.sgbReference ==
                                NativeUserSettingsRepository.SgbReference.RBI_REFERENCE_REQUIRED
                            if (requireRbi) {
                                Text("SGB RBI redemption reference not verified: value unavailable. " +
                                    "A recorded market close is not an RBI reference.",
                                    modifier = Modifier.testTag("m31-sgb-unverified"))
                            } else {
                                Text("Manual/last price: ₹${NativeBooksRepository.formatMinor(h.marketPriceMinor, 4)} " +
                                    "as of ${h.priceDate ?: "unknown"} (${h.priceSource ?: "no source"})")
                                Text(NativeExchangePriceDisclosure.text(h, LocalDate.now()),
                                    modifier = Modifier.testTag("m30-price-provenance-${h.id}"))
                            }
                            if (h.category == "GOLD_SGB" &&
                                preferences.ibjaReference ==
                                NativeUserSettingsRepository.IbjaReference.OWNER_REVIEW_REQUIRED)
                                Text("IBJA owner review selected; no verified IBJA feed " +
                                    "or rate is applied automatically.",
                                    modifier = Modifier.testTag("m31-ibja-review"))
                            if (h.category == "REAL_ESTATE") {
                                // Ownership metadata is NOT a legal title check
                                // and does not rewrite family balance, owner, or ledger.
                                val share = runCatching {
                                    val info = NativeHoldingSpecialist.read(
                                        h.detailsJson, h.category, h.subcategory)
                                    NativeOwnershipEconomicShare.preview(h.category,
                                        info["ownership"], info["ownershipPercent"],
                                        h.quantityScaled, h.marketPriceMinor)
                                }.getOrNull()
                                if (share == null)
                                    Text("Owner economic share N/A — recorded ownership " +
                                        "percent and a nonzero price are required.",
                                        modifier = Modifier.testTag("m27-owner-share-unverified"))
                                else Text("Indicative owner's ${share.fractionPercent}% share " +
                                    "of last recorded gross value: ₹" +
                                    NativeBooksRepository.formatMinor(
                                        share.indicativeOwnerValueMinor, 4) +
                                    " (not proof of title or a separate family total)",
                                    modifier = Modifier.testTag("m27-owner-share-indicative"))
                            }
                            if (h.category == "FIXED_INCOME" &&
                                h.subcategory in setOf("PPF", "Sukanya Samriddhi (SSY)")) {
                                Text("PPF/SSY historic interest not verified: each FY " +
                                    "requires its own rate, dated deposits and passbook " +
                                    "reconciliation. Current rate is not back-applied; " +
                                    "no interest or contribution is auto-posted.",
                                    modifier = Modifier.testTag("m27-ppf-ssy-history-warning"))
                            }
                            Text("Custody link: " + (lensCustody.firstOrNull { it.id == h.accountId }
                                ?.custodian ?: "None") + " • Goal link: " +
                                (data.goals.firstOrNull { it.id == h.goalId }?.name ?: "None"),
                                modifier = Modifier.testTag("m27-holding-links"))
                            h.location?.let { Text("Location: " + it) }
                            h.notes?.let { Text("Holding notes: " + it) }
                            if (h.archived) Text("Archived (history retained)")
                            if (h.needsReview) Text("Reconciliation review needed",
                                color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = {
                                val requestedLens = ownerFilter
                                val requestedHolding = h.id
                                historyId = requestedHolding
                                history = emptyList()
                                busy = true
                                scope.launch {
                                    try {
                                        val loaded = withContext(Dispatchers.IO) {
                                            books.history(email, requestedHolding)
                                        }
                                        if (ownerFilter == requestedLens &&
                                            historyId == requestedHolding) history = loaded
                                    } catch (_: Exception) {
                                        if (ownerFilter == requestedLens)
                                            error = "Event history unavailable. Verify the vault again."
                                    } finally { busy = false }
                                }
                            }) { Text("View audit history") }
                            OutlinedButton(enabled = !busy, onClick = {
                                val requestedLens = ownerFilter
                                busy = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            books.moneyWeightedReturn(email, h.id)
                                        }
                                        if (ownerFilter == requestedLens)
                                            xirrResults = xirrResults + (h.id to result)
                                    } catch (_: Exception) {
                                        if (ownerFilter == requestedLens)
                                            error = "Money-weighted return unavailable. " +
                                                "Review the ledger and recorded price."
                                    } finally { busy = false }
                                }
                            }) { Text("Calculate money-weighted return (XIRR)") }
                            if (h.id in xirrResults) {
                                val computed = xirrResults[h.id]
                                Text(if (computed == null) "XIRR: N/A — insufficient " +
                                    "verified cash flows, basis or current price"
                                else "XIRR: " + String.format(
                                    java.util.Locale.ROOT, "%.2f%%", computed) +
                                    " (recorded last price, not a live quote)")
                            }
                        }
                        if (historyId.isNotEmpty() && list.any { it.id == historyId }) {
                            Text("Reconciliation review: " +
                                "${history.count { it.sourceBacked }} source-referenced event(s), " +
                                "${history.count { it.kind == "SNAPSHOT_ADJUSTMENT" }} " +
                                "registrar snapshot(s). Source-only adjustments cannot be " +
                                "reclassified as purchases or edited manually.",
                                modifier = Modifier.testTag("m27-reconciliation-read-only"))
                            NativeHoldingAuditHistory(history)
                            TextButton(onClick = { historyId = ""; history = emptyList() }) {
                                Text("Close history")
                            }
                        }
                        key(ownerFilter) {
                            HoldingForm(lensPeople, lensCustody, busy) { form ->
                                if (ownerFilter.isEmpty() || form.personId == ownerFilter)
                                    persist { books.createHolding(email, form) }
                            }
                            HoldingEditForm(list, lensCustody, data.goals, busy,
                                update = { id, account, notes, details, location -> persist {
                                    books.updateHoldingMetadata(email, id, account, notes, details, location)
                                } },
                                assign = { id, goal -> persist { books.assignGoal(email, id, goal) } },
                                archive = { id, value -> persist { books.setHoldingArchived(email, id, value) } })
                        }

}
