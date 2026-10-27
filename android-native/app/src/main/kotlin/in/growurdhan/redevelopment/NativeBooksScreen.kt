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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.model.NativeAssetTaxonomy
import `in`.growurdhan.model.NativeInterestPayoutPlanner
import `in`.growurdhan.model.NativeTaxLotTrace
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
    onLock: () -> Unit, onReplaceRecovery: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeBooksRepository.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ledgerRevision by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Home") }
    var ownerFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All") }
    var holdingAccountFilter by remember { mutableStateOf("") }
    var holdingStatusFilter by remember { mutableStateOf("All") }
    var holdingSort by remember { mutableStateOf("Name") }
    var search by remember { mutableStateOf("") }
    var historyId by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<NativeBooksRepository.EventRow>>(emptyList()) }
    var xirrResults by remember { mutableStateOf<Map<String, Double?>>(emptyMap()) }

    suspend fun refresh() {
        val fresh = withContext(Dispatchers.IO) { books.read(email) }
        snapshot = fresh
    }
    fun persist(task: () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
                refresh()
                ledgerRevision += 1 // Refresh correction choices only after a committed edit.
            } catch (_: Exception) {
                error = "Cannot save. The vault may have locked or this record is invalid. No partial edit was committed."
            } finally { busy = false }
        }
    }
    LaunchedEffect(ownerFilter) {
        // Never show a previous member's audit history or calculated return
        // after switching the active lens.
        historyId = ""
        history = emptyList()
        xirrResults = emptyMap()
        // A member switch must revoke even the account filter selection.
        holdingAccountFilter = ""
    }
    LaunchedEffect(email) {
        xirrResults = emptyMap()
        try { refresh() } catch (_: Exception) {
            error = "Unable to open encrypted books. Lock and verify this device again."
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
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
                Button(onClick = onLock, modifier = Modifier.heightIn(min = 48.dp)) { Text("Lock vault") }
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
                NativeBooksNavigation(tab) { tab = it }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                when (tab) {
                    "Home" -> {
                        val selected = data.holdings.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                        val warnings = selected.count { it.unknownCost || it.needsReview || it.priceDate == null }
                        Text("${selected.size} holdings • ${lensPeople.size} active ${if (lensPeople.size == 1) "member" else "members"} in view",
                            style = MaterialTheme.typography.titleLarge)
                        Text("$warnings holding(s) need review or cost/price confirmation")
                        Text("Home displays book records. No market value is calculated from an absent quote.")
                        selected.take(12).forEach { h ->
                            Text("${h.name}: ${NativeBooksRepository.formatMinor(h.quantityScaled, 8)} units " +
                                "• ${h.personName}")
                        }
                    }
                    "Family" -> {
                        Text("Family members", style = MaterialTheme.typography.titleLarge)
                        active.filter { ownerFilter.isEmpty() || it.id == ownerFilter }
                            .forEach { m ->
                            Text("${m.name} • ${m.relation} • ${m.kind}" +
                                (if (m.isPrimary) " (Primary)" else "") +
                                (m.maskedPan?.let { " • $it" } ?: ""))
                        }
                        if (ownerFilter.isEmpty()) NativeEmergencyDossierCard(data)
                        else Text("Household-wide emergency dossier is available in Whole household lens.")
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
                    "Accounts" -> {
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
                    "Goals" -> {
                        Text("Family goals", style = MaterialTheme.typography.titleLarge)
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
                    "Holdings" -> {
                        Text("Holdings", style = MaterialTheme.typography.titleLarge)
                        CategoryPicker(categoryFilter) { categoryFilter = it }
                        Choice("Account filter", listOf("" to "All custody accounts",
                            NativeHoldingListFilter.NO_ACCOUNT to "No account") +
                            lensCustody.map { it.id to it.custodian },
                            holdingAccountFilter) { holdingAccountFilter = it }
                        Choice("Archive status", listOf("All", "Active", "Archived").map { it to it },
                            holdingStatusFilter) { holdingStatusFilter = it }
                        Choice("Sort holdings", listOf("Name", "Recorded value", "Recent price",
                            "Category").map { it to it }, holdingSort) { holdingSort = it }
                        OutlinedTextField(search, { search = it }, label = { Text("Search holdings") },
                            modifier = Modifier.fillMaxWidth())
                        val list = NativeHoldingListFilter.apply(data.holdings, ownerFilter,
                            categoryFilter, holdingAccountFilter, holdingStatusFilter, search,
                            holdingSort)
                        Text("${list.size} holding(s) in filtered view — archived and zero-unit history retained")
                        list.forEach { h ->
                            HorizontalDivider()
                            Text("${h.name} • ${h.personName}", style = MaterialTheme.typography.titleMedium)
                            Text("${h.subcategory} • ${NativeBooksRepository.formatMinor(h.quantityScaled, 8)} units")
                            Text(if (h.unknownCost) "Opening cost unknown — review before using gains"
                                else "Recorded FIFO cost: ₹${NativeBooksRepository.formatMinor(h.knownCostMinor, 4)}")
                            Text("Manual/last price: ₹${NativeBooksRepository.formatMinor(h.marketPriceMinor, 4)} " +
                                "as of ${h.priceDate ?: "unknown"} (${h.priceSource ?: "no source"})")
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
                    "Documents" -> {
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
                    "Settings" -> {
                        if (ownerFilter.isNotEmpty()) Text("Device settings apply to the whole household, regardless of the selected member lens.")
                        NativeAmfiNavCard(email, books) {
                            scope.launch {
                                try { refresh() } catch (_: Exception) {
                                    error = "Cannot refresh encrypted NAV display"
                                }
                            }
                        }
                        NativeGmailOptInCard(email, books, data.people)
                        NativePrivateDiagnosticsSettings(email, diagnostics)
                    }
                    "Records" -> key(ownerFilter) {
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
            }
        }
    }
}

@Composable
private fun Choice(label: String, choices: List<Pair<String, String>>,
    selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium)
    OutlinedButton(onClick = { expanded = true }, enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).testTag("choice-$label").semantics {
            stateDescription = "$label: " +
                (choices.firstOrNull { it.first == selected }?.second ?: "Choose")
        }) {
        Text(choices.firstOrNull { it.first == selected }?.second ?: "Choose")
    }
    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
        choices.forEach { (key, display) ->
            DropdownMenuItem(text = { Text(display) }, onClick = {
                onSelect(key)
                expanded = false
            })
        }
    }
}
@Composable
private fun OwnerPicker(people: List<NativeBooksRepository.Person>, id: String,
    onChange: (String) -> Unit) =
    Choice("Member lens", listOf("" to "Whole household") +
        people.map { it.id to it.name }, id, onSelect = onChange)

@Composable
private fun CategoryPicker(value: String, set: (String) -> Unit) =
    Choice("Category", listOf("All" to "All") +
        NativeAssetTaxonomy.subcategories.keys.map { it to it.replace("_", " ") },
        value, onSelect = set)

@Composable
private fun PersonForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
    save: (NativeBooksRepository.NewPerson) -> Unit) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Spouse") }
    var kind by remember { mutableStateOf("INDIVIDUAL") }
    var dob by remember { mutableStateOf("") }
    var guardian by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    Text("Add family member", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(name, { name = it }, label = { Text("Name") },
        modifier = Modifier.fillMaxWidth())
    Choice("Relationship", listOf("Spouse", "Parent", "Child", "HUF", "Joint")
        .map { it to it }, relation, !busy) { relation = it }
    Choice("Legal classification", listOf("INDIVIDUAL", "HUF", "PROPRIETORSHIP",
        "PRIVATE_TRUST", "PARTNERSHIP_FIRM").map { it to it }, kind, !busy) { kind = it }
    OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth (YYYY-MM-DD; optional)") },
        modifier = Modifier.fillMaxWidth())
    Choice("Guardian (required for minors)", listOf("" to "Not applicable") +
        people.map { it.id to it.name }, guardian, !busy) { guardian = it }
    OutlinedTextField(pan, { pan = it }, label = { Text("Statement PAN (optional, stored encrypted)") },
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && name.isNotBlank(), onClick = {
        save(NativeBooksRepository.NewPerson(name, relation, kind, dob.ifBlank { null },
            guardian.ifBlank { null }, pan.ifBlank { null }))
        name = ""; dob = ""; pan = ""
    }) { Text("Save member") }
}

@Composable
private fun CustodyForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
    save: (NativeBooksRepository.NewCustody) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var custodian by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("FOLIO") }
    var masked by remember { mutableStateOf("") }
    var nominee by remember { mutableStateOf("") }
    var dpId by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var boId by remember { mutableStateOf("") }
    Text("Link custody account", style = MaterialTheme.typography.titleMedium)
    Choice("Account owner", people.map { it.id to it.name }, owner, !busy) { owner = it }
    Choice("Custody type", listOf("DEMAT", "FOLIO", "BANK", "OTHER", "NPS", "BROKER")
        .map { it to it }, type, !busy) { type = it }
    OutlinedTextField(custodian, { custodian = it }, label = { Text("Custodian / issuer") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(masked, { masked = it }, label = { Text("Masked account reference (e.g. ****1234)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(nominee, { nominee = it }, label = { Text("Nominee (optional)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(dpId, { dpId = it }, label = { Text("DP ID (encrypted)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID (encrypted)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(boId, { boId = it }, label = { Text("BO ID (encrypted)") },
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && owner.isNotBlank() && custodian.isNotBlank(), onClick = {
        save(NativeBooksRepository.NewCustody(owner, type, custodian,
            masked.ifBlank { null }, dpId.ifBlank { null }, clientId.ifBlank { null },
            boId.ifBlank { null }, nominee.ifBlank { null }))
        custodian = ""; masked = ""; nominee = ""; dpId = ""; clientId = ""; boId = ""
    }) { Text("Save account") }
}

@Composable
private fun GoalForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
    selectedOwnerId: String, save: (String?, String, Long, String, String) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    Text("Create goal", style = MaterialTheme.typography.titleMedium)
    if (selectedOwnerId.isEmpty()) {
        Choice("Goal owner", listOf("" to "Household") +
            people.map { it.id to it.name }, owner, !busy) { owner = it }
    } else {
        Text("Goal owner: " + (people.firstOrNull { it.id == selectedOwnerId }?.name
            ?: "Selected member unavailable"))
    }
    OutlinedTextField(name, { name = it }, label = { Text("Goal") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(target, { target = it }, label = { Text("Target ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(day, { day = it }, label = { Text("Target date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    Choice("Priority", listOf("LOW", "MEDIUM", "HIGH").map { it to it },
        priority, !busy) { priority = it }
    Button(enabled = !busy && name.isNotBlank() && target.isNotBlank() && day.isNotBlank(),
        onClick = {
            val minor = runCatching { NativeBooksRepository.parseMinor(target, 4) }.getOrNull()
            if (minor != null) {
                save(if (selectedOwnerId.isEmpty()) owner.ifBlank { null }
                    else selectedOwnerId, name, minor, day, priority)
                name = ""; target = ""; day = ""
            }
        }) { Text("Save goal") }
}

@Composable
private fun SpecialistFields(category: String, subcategory: String,
    values: Map<String, String>, onChange: (String, String) -> Unit) {
    val fields = NativeHoldingSpecialist.fields(category, subcategory)
    if (fields.isEmpty()) return
    Text("Asset-specific details (only relevant fields)",
        style = MaterialTheme.typography.titleMedium)
    fields.forEach { field ->
        val current = values[field.key].orEmpty()
        when (field.kind) {
            NativeHoldingSpecialist.Kind.ENUM, NativeHoldingSpecialist.Kind.BOOLEAN -> {
                val items = if (field.kind == NativeHoldingSpecialist.Kind.BOOLEAN)
                    listOf("true" to "Yes", "false" to "No")
                else field.options.map { it to it }
                Choice(field.label, listOf("" to "Not recorded") + items,
                    current, onSelect = { onChange(field.key, it) })
            }
            else -> OutlinedTextField(current, { onChange(field.key, it) },
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth().testTag("m27-specialist-${field.key}"))
        }
    }
}

@Composable
internal fun HoldingForm(people: List<NativeBooksRepository.Person>,
    custody: List<NativeBooksRepository.Custody>, busy: Boolean,
    save: (NativeBooksRepository.NewHolding) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUITY") }
    var sub by remember { mutableStateOf("Other") }
    var quantity by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("0") }
    var knownCost by remember { mutableStateOf("Unknown") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
    var folioForStatements by remember { mutableStateOf("") }
    var openingPrice by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var accountMasked by remember { mutableStateOf("") }
    var maturity by remember { mutableStateOf("") }
    var couponBps by remember { mutableStateOf("") }
    var interestBps by remember { mutableStateOf("") }
    var emiMinor by remember { mutableStateOf("") }
    var emiState by remember { mutableStateOf("") }
    var emiCount by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var specialistValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var specialistError by remember { mutableStateOf("") }
    var reviewed by remember { mutableStateOf<NativeBooksRepository.NewHolding?>(null) }
    var reviewedSignature by remember { mutableStateOf<String?>(null) }
    Text("New holding — review fields before saving", style = MaterialTheme.typography.titleMedium)
    Choice("Owner", people.map { it.id to it.name }, owner, !busy) { owner = it; account = "" }
    Choice("Custody account", listOf("" to "No custody account") +
        custody.filter { it.personId == owner }.map { it.id to it.custodian },
        account, !busy) { account = it }
    Choice("Asset category", NativeAssetTaxonomy.subcategories.keys.map { it to it }, category,
        !busy) {
        category = it
        sub = NativeAssetTaxonomy.subcategories.getValue(it).first()
        specialistValues = NativeHoldingSpecialist.defaults(category, sub)
        specialistError = ""
    }
    Choice("Subcategory", NativeAssetTaxonomy.subcategories.getValue(category).map { it to it },
        sub, !busy) {
        sub = it
        specialistValues = NativeHoldingSpecialist.defaults(category, sub)
        specialistError = ""
    }
    OutlinedTextField(name, { name = it }, label = { Text("Instrument / holding name") },
        modifier = Modifier.fillMaxWidth().testTag("m27-holding-name"))
    OutlinedTextField(quantity, { quantity = it }, label = { Text("Opening units (may be 0)") },
        modifier = Modifier.fillMaxWidth().testTag("m27-holding-units"))
    OutlinedTextField(price, { price = it }, label = { Text("Current / last price per unit ₹ (valuation only)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(date, { date = it }, label = { Text("Acquisition date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    Choice("Opening cost", listOf("Unknown" to "Unknown", "Known" to "Known"), knownCost,
        !busy) {
        knownCost = it
        if (it == "Unknown") openingPrice = ""
    }
    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(isin, { isin = it }, label = { Text("ISIN (optional, 12 characters)") },
        modifier = Modifier.fillMaxWidth())
    if (category == "MUTUAL_FUND") {
        OutlinedTextField(folioForStatements, { folioForStatements = it },
            label = { Text("Exact MF folio for statement matching (optional)") },
            modifier = Modifier.fillMaxWidth())
        Text("Enter only the folio shown on your statement. Never substitute a DP/client or masked reference.")
    }
    if (knownCost == "Known") {
        OutlinedTextField(openingPrice, { openingPrice = it },
            label = { Text("Verified acquisition cost per unit ₹ (required)") },
            modifier = Modifier.fillMaxWidth().testTag("m27-opening-cost"))
        Text("Enter a verified acquisition cost. Current market price is never used as cost.")
    } else {
        Text("Opening acquisition cost: Unknown. No purchase price or gain will be inferred.")
    }
    OutlinedTextField(institution, { institution = it }, label = { Text("Institution / issuer") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(accountMasked, { accountMasked = it },
        label = { Text("Masked account reference (if relevant)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(maturity, { maturity = it }, label = { Text("Maturity YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(couponBps, { couponBps = it }, label = { Text("Coupon rate in basis points") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(interestBps, { interestBps = it },
        label = { Text("Interest rate in basis points") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(emiMinor, { emiMinor = it }, label = { Text("Monthly EMI ₹") },
        modifier = Modifier.fillMaxWidth())
    Choice("EMI status", listOf("" to "Not applicable") +
        listOf("PAYING", "PAUSED", "STOPPED").map { it to it }, emiState, !busy) {
        emiState = it
    }
    OutlinedTextField(emiCount, { emiCount = it },
        label = { Text("Remaining installments") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(location, { location = it }, label = { Text("Property/location") },
        modifier = Modifier.fillMaxWidth())
    SpecialistFields(category, sub, specialistValues) { key, value ->
        specialistValues = specialistValues + (key to value)
        specialistError = ""
    }
    if (specialistError.isNotEmpty())
        Text(specialistError, color = MaterialTheme.colorScheme.error)
    val incomeDraft = runCatching {
        val details = NativeHoldingSpecialist.merge(null, category, sub, specialistValues)
        NativeHoldingSpecialist.monthlyIncomeSchedule(
            details, date, LocalDate.now().toString())
    }.getOrNull()
    if (incomeDraft != null) {
        Text("Monthly income schedule (not booked): ₹" +
            NativeBooksRepository.formatMinor(incomeDraft.amountMinor, 4) +
            " per scheduled date; " + incomeDraft.scheduledThroughAsOf +
            " dates through today, ₹" +
            NativeBooksRepository.formatMinor(incomeDraft.scheduledTotalMinor, 4) +
            " projected only. Next: " + (incomeDraft.nextDate ?: "none"))
        Text("This preview never creates dividend, interest, cash or valuation events. " +
            "Record actual receipts only after owner review.")
    }
    // Any edited field invalidates an earlier review, including hidden category details.
    val currentSignature = listOf(owner, account, name, category, sub, quantity, price,
        knownCost, date, notes, isin, folioForStatements, openingPrice, institution,
        accountMasked, maturity, couponBps, interestBps, emiMinor, emiState, emiCount,
        location, specialistValues.toSortedMap().toString()).joinToString("\u001f")
    Button(enabled = !busy && owner.isNotBlank() && name.isNotBlank() &&
        (knownCost == "Unknown" || openingPrice.isNotBlank()),
        modifier = Modifier.testTag("m27-review-holding"), onClick = {
        val qty = runCatching { NativeBooksRepository.parseMinor(quantity, 8) }.getOrNull()
        val minor = runCatching { NativeBooksRepository.parseMinor(price, 4) }.getOrNull()
        val opened = if (knownCost == "Unknown") null else runCatching {
            NativeBooksRepository.parseMinor(openingPrice, 4)
        }.getOrNull()
        val emi = if (emiMinor.isBlank()) null else runCatching {
            NativeBooksRepository.parseMinor(emiMinor, 4)
        }.getOrNull()
        val coupon = couponBps.toIntOrNull()
        val interest = interestBps.toIntOrNull()
        val count = emiCount.toIntOrNull()
        val specialist = runCatching {
            NativeHoldingSpecialist.merge(null, category, sub, specialistValues)
        }
        if (specialist.isFailure) specialistError = "Check the asset-specific fields before saving."
        if (specialist.isSuccess && qty != null && minor != null &&
            (knownCost == "Unknown" || opened != null) &&
            (emiMinor.isBlank() || emi != null) &&
            (couponBps.isBlank() || coupon != null) &&
            (interestBps.isBlank() || interest != null) &&
            (emiCount.isBlank() || count != null)) {
            reviewed = NativeBooksRepository.NewHolding(owner, account.ifBlank { null }, name,
                category, sub, qty, minor, date, knownCost == "Known",
                isin = isin.ifBlank { null }, notes = notes.ifBlank { null },
                folioForStatements = if (category == "MUTUAL_FUND")
                    folioForStatements.ifBlank { null } else null,
                detailsJson = specialist.getOrNull(),
                openingUnitPriceMinor = opened,
                institutionName = institution.ifBlank { null },
                accountNumberMasked = accountMasked.ifBlank { null },
                maturityDate = maturity.ifBlank { null },
                couponRateBps = coupon, interestRateBps = interest,
                monthlyEmiMinor = emi, emiStatus = emiState.ifBlank { null },
                remainingEmiCount = count, location = location.ifBlank { null })
            reviewedSignature = currentSignature
        }
    }) { Text("Review holding details (no data saved)") }
    val approved = reviewed
    if (approved != null && reviewedSignature == currentSignature) {
        Text("Confirm holding before saving", style = MaterialTheme.typography.titleMedium)
        Text("Owner: " + (people.firstOrNull { it.id == approved.personId }?.name ?: "Unknown"))
        Text("Instrument: " + approved.name + " • " + approved.category + " / " + approved.subcategory)
        Text("Opening quantity: " + NativeBooksRepository.formatMinor(approved.quantityScaled, 8))
        Text("Current price (not acquisition cost): ₹" +
            NativeBooksRepository.formatMinor(approved.priceMinor, 4))
        Text(if (approved.knownOpeningCost) "Verified acquisition cost: ₹" +
            NativeBooksRepository.formatMinor(approved.openingUnitPriceMinor!!, 4) +
            " per unit" else "Acquisition cost: Unknown (no inferred basis)")
        Text("Confirming will append an immutable opening event only for nonzero units.")
        Button(enabled = !busy, modifier = Modifier.testTag("m27-confirm-holding"),
            onClick = {
                // Preserve input for correction if asynchronous SQLCipher save is rejected.
                reviewed = null
                reviewedSignature = null
                save(approved)
            }) { Text("Confirm save holding") }
    }
}

@Composable
internal fun EventForm(holdings: List<NativeBooksRepository.Holding>, busy: Boolean,
    historyRevision: Int = 0,
    loadHistory: suspend (String) -> List<NativeBooksRepository.EventRow>,
    add: (NativeBooksRepository.NewEvent) -> Unit,
    price: (String, Long, String) -> Unit) {
    var holding by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("BUY") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var units by remember { mutableStateOf("0") }
    var unitPrice by remember { mutableStateOf("0") }
    var amount by remember { mutableStateOf("0") }
    var charges by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var correctionId by remember { mutableStateOf("") }
    var manualPrice by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<NativeBooksRepository.EventRow>>(emptyList()) }
    var reviewed by remember { mutableStateOf<NativeBooksRepository.NewEvent?>(null) }
    var reviewedSignature by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf("") }
    val selectedHoldingInLens = holdings.any { it.id == holding }

    LaunchedEffect(holding, historyRevision, selectedHoldingInLens) {
        // Do not keep correction candidates after a lens switch or stale save.
        history = emptyList()
        if (selectedHoldingInLens) {
            history = runCatching { withContext(Dispatchers.IO) { loadHistory(holding) } }
                .getOrDefault(emptyList())
        }
    }
    val byId = history.associateBy { it.id }
    fun correctedKind(target: String): String? {
        var cursor = byId[target] ?: return null
        val visited = mutableSetOf<String>()
        while (cursor.kind == "CORRECTION") {
            if (!visited.add(cursor.id)) return null
            val previousId = cursor.supersedes ?: return null
            cursor = byId[previousId] ?: return null
        }
        return cursor.kind
    }
    val effectiveKind = if (kind == "CORRECTION") correctedKind(correctionId) else kind
    val split = effectiveKind == "SPLIT"
    val cashOnly = effectiveKind in setOf("DIVIDEND", "INTEREST", "REPAYMENT")
    val valuation = effectiveKind == "VALUATION_UPDATE"
    val bonus = effectiveKind == "BONUS"
    val quantityField = effectiveKind in setOf("BUY", "SELL", "OPENING_BALANCE", "BONUS", "SPLIT")
    val priceField = effectiveKind in setOf("BUY", "SELL", "OPENING_BALANCE", "SPLIT", "VALUATION_UPDATE")
    val cashField = effectiveKind in setOf("BUY", "SELL", "OPENING_BALANCE",
        "DIVIDEND", "INTEREST", "REPAYMENT")
    val feeField = effectiveKind in setOf("BUY", "SELL", "OPENING_BALANCE")
    val signature = listOf(holding, kind, correctionId, day, units, unitPrice,
        amount, charges, note).joinToString("\u001f")

    Choice("Holding", holdings.map { it.id to "${it.name} • ${it.personName}" },
        holding, !busy) { holding = it; correctionId = ""; reviewed = null }
    Choice("Event kind", listOf("BUY", "SELL", "DIVIDEND", "INTEREST", "BONUS", "SPLIT",
        "REPAYMENT", "OPENING_BALANCE", "VALUATION_UPDATE", "CORRECTION")
        .map { it to it }, kind, !busy) {
        kind = it
        units = "0"; unitPrice = "0"; amount = "0"; charges = "0"
        correctionId = ""; reviewed = null; inputError = ""
    }
    if (kind == "CORRECTION") {
        Choice("Recorded event to correct", listOf("" to "Select exact recorded event") +
            history.map { it.id to "${it.date} • ${it.kind} • ${it.id}" },
            correctionId, !busy) { target ->
            correctionId = target
            val original = byId[target]
            if (original != null) {
                val resolved = correctedKind(target)
                units = if (resolved == "SPLIT") original.quantityScaled.toString()
                    else NativeBooksRepository.formatMinor(original.quantityScaled, 8)
                unitPrice = if (resolved == "SPLIT") original.unitPriceMinor.toString()
                    else NativeBooksRepository.formatMinor(original.unitPriceMinor, 4)
                amount = NativeBooksRepository.formatMinor(original.amountMinor, 4)
                charges = NativeBooksRepository.formatMinor(original.chargesMinor, 4)
                day = original.date
            }
            reviewed = null
        }
        Text("Corrections append a replacement; the original and its full event ID remain " +
            "immutable. Imported, registrar and automatic-source events cannot be manually corrected.")
    }
    OutlinedTextField(day, { day = it }, label = { Text("Event date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth().testTag("m27-event-date"))
    if (quantityField) {
        OutlinedTextField(units, { units = it }, label = {
            Text(if (split) "Split ratio numerator (whole integer)"
                else "Recorded units (up to 8 decimal places)")
        }, modifier = Modifier.fillMaxWidth().testTag("m27-event-units"))
    }
    if (priceField) {
        OutlinedTextField(unitPrice, { unitPrice = it }, label = {
            Text(if (split) "Split ratio denominator (whole integer)"
                else if (valuation) "Dated observed price per unit ₹"
                else "Transaction price per unit ₹")
        }, modifier = Modifier.fillMaxWidth().testTag("m27-event-price"))
    }
    if (cashField) {
        OutlinedTextField(amount, { amount = it }, label = {
            Text(if (cashOnly) "Cash amount received/paid ₹" else "Total transaction amount ₹")
        }, modifier = Modifier.fillMaxWidth().testTag("m27-event-amount"))
    }
    if (feeField) {
        OutlinedTextField(charges, { charges = it }, label = { Text("Transaction charges ₹") },
            modifier = Modifier.fillMaxWidth().testTag("m27-event-fee"))
    }
    if (bonus) Text("Bonus units create no new purchase consideration.")
    if (split) Text("Splits alter existing units by an exact ratio, not cash or tax basis.")
    if (cashOnly) Text("Income or repayment is cash only: no units or market price will change.")
    if (valuation) Text("A dated valuation is a price observation, not a purchase, sale or cash flow.")
    OutlinedTextField(note, { note = it }, label = { Text("Source or notes") },
        modifier = Modifier.fillMaxWidth())
    if (inputError.isNotEmpty()) Text(inputError, color = MaterialTheme.colorScheme.error)
    Button(enabled = !busy && selectedHoldingInLens &&
        (kind != "CORRECTION" || effectiveKind != null),
        modifier = Modifier.testTag("m27-review-event"), onClick = {
        val parsed = runCatching {
            require(LocalDate.parse(day).toString() == day)
            val q = if (quantityField) {
                if (split) units.toLong() else NativeBooksRepository.parseMinor(units, 8)
            } else 0L
            val p = if (priceField) {
                if (split) unitPrice.toLong() else NativeBooksRepository.parseMinor(unitPrice, 4)
            } else 0L
            val m = if (cashField) NativeBooksRepository.parseMinor(amount, 4) else 0L
            val fee = if (feeField) NativeBooksRepository.parseMinor(charges, 4) else 0L
            require(p >= 0L && fee >= 0L && (!cashOnly || m >= 0L))
            require(!split || (q > 0 && p > 0))
            NativeBooksRepository.NewEvent(holding, kind, day, q, p, m, fee,
                supersedes = if (kind == "CORRECTION") correctionId else null,
                notes = note.ifBlank { null })
        }
        reviewed = parsed.getOrNull()
        reviewedSignature = if (parsed.isSuccess) signature else null
        inputError = if (parsed.isFailure) "Review event-specific values and date." else ""
    }) { Text("Review event (does not write)") }
    val pending = reviewed
    if (pending != null && reviewedSignature == signature) {
        Text("Confirm immutable event", style = MaterialTheme.typography.titleMedium)
        Text("${pending.kind} • ${pending.date} • holding ${holdings.firstOrNull { it.id == holding }?.name}")
        Text("Units ${NativeBooksRepository.formatMinor(pending.quantityScaled, 8)} • " +
            "price ₹${NativeBooksRepository.formatMinor(pending.unitPriceMinor, 4)} • " +
            "amount ₹${NativeBooksRepository.formatMinor(pending.amountMinor, 4)}")
        Text("Cash/lot effects will be verified inside SQLCipher before commit.")
        Button(enabled = !busy, modifier = Modifier.testTag("m27-confirm-event"), onClick = {
            reviewed = null; reviewedSignature = null
            add(pending)
        }) { Text("Confirm append-only event") }
    }
    HorizontalDivider()
    Text("Separate manual dated price", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(manualPrice, { manualPrice = it },
        label = { Text("Manual recorded price per unit ₹") },
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && selectedHoldingInLens, onClick = {
        val p = runCatching { NativeBooksRepository.parseMinor(manualPrice, 4) }.getOrNull()
        if (p != null) price(holding, p, day)
    }) { Text("Save manual price and source date") }
}

@Composable
private fun PersonEditForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
    update: (String, NativeBooksRepository.NewPerson) -> Unit, archive: (String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    val selected = people.firstOrNull { it.id == id }
    Text("Edit / archive member", style = MaterialTheme.typography.titleMedium)
    Choice("Member to edit", people.map { it.id to it.name }, id, !busy) {
        id = it
        name = people.firstOrNull { p -> p.id == it }?.name.orEmpty()
        dob = ""
    }
    if (selected != null) {
        OutlinedTextField(name, { name = it }, label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dob, { dob = it },
            label = { Text("Correct DOB (optional YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && name.isNotBlank(), onClick = {
            update(id, NativeBooksRepository.NewPerson(name, selected.relation,
                selected.kind, dob.ifBlank { null }))
        }) { Text("Save member details") }
        if (!selected.isPrimary) {
            OutlinedButton(enabled = !busy, onClick = { archive(id); id = "" }) {
                Text("Archive only if unlinked")
            }
        }
    }
}

@Composable
private fun CustodyEditForm(accounts: List<NativeBooksRepository.Custody>, busy: Boolean,
    update: (String, NativeBooksRepository.NewCustody) -> Unit,
    close: (NativeBooksRepository.Custody) -> Unit) {
    var id by remember { mutableStateOf("") }
    var custodian by remember { mutableStateOf("") }
    var nominee by remember { mutableStateOf("") }
    val chosen = accounts.firstOrNull { it.id == id }
    Text("Edit / close custody account", style = MaterialTheme.typography.titleMedium)
    Choice("Custody record", accounts.map { it.id to "${it.custodian} • ${it.type}" },
        id, !busy) {
        id = it
        custodian = accounts.firstOrNull { a -> a.id == it }?.custodian.orEmpty()
        nominee = ""
    }
    if (chosen != null) {
        OutlinedTextField(custodian, { custodian = it }, label = { Text("Custodian") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nominee, { nominee = it }, label = { Text("Updated nominee (optional)") },
            modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && custodian.isNotBlank(), onClick = {
            update(id, NativeBooksRepository.NewCustody(chosen.personId, chosen.type,
                custodian, nominee = nominee.ifBlank { null }))
        }) { Text("Save custody details") }
        OutlinedButton(enabled = !busy, onClick = { close(chosen); id = "" }) {
            Text("Close only if no open units")
        }
    }
}

@Composable
private fun GoalEditForm(goals: List<NativeBooksRepository.Goal>, busy: Boolean,
    update: (NativeBooksRepository.Goal, String, Long, String, String) -> Unit,
    remove: (String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    val goal = goals.firstOrNull { it.id == id }
    Text("Edit / remove goal", style = MaterialTheme.typography.titleMedium)
    Choice("Goal", goals.map { it.id to it.name }, id, !busy) {
        id = it
        val g = goals.firstOrNull { item -> item.id == it }
        name = g?.name.orEmpty()
        target = g?.targetMinor?.let { amount -> NativeBooksRepository.formatMinor(amount, 4) }.orEmpty()
        day = g?.targetDate.orEmpty()
        priority = g?.priority ?: "MEDIUM"
    }
    if (goal != null) {
        OutlinedTextField(name, { name = it }, label = { Text("Goal") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(target, { target = it }, label = { Text("Target ₹") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(day, { day = it }, label = { Text("Target date YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth())
        Choice("Priority", listOf("LOW", "MEDIUM", "HIGH").map { it to it },
            priority, !busy) { priority = it }
        Button(enabled = !busy && name.isNotBlank(), onClick = {
            val minor = runCatching { NativeBooksRepository.parseMinor(target, 4) }.getOrNull()
            if (minor != null) update(goal, name, minor, day, priority)
        }) { Text("Save goal corrections") }
        OutlinedButton(enabled = !busy, onClick = { remove(id); id = "" }) {
            Text("Remove unlinked goal")
        }
    }
}

@Composable
internal fun HoldingEditForm(holdings: List<NativeBooksRepository.Holding>,
    accounts: List<NativeBooksRepository.Custody>, goals: List<NativeBooksRepository.Goal>,
    busy: Boolean, update: (String, String?, String?, String?, String?) -> Unit,
    assign: (String, String?) -> Unit, archive: (String, Boolean) -> Unit) {
    var id by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var specialistValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var specialistError by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var goalId by remember { mutableStateOf("") }
    var pendingArchive by remember { mutableStateOf<String?>(null) }
    val holding = holdings.firstOrNull { it.id == id }
    Text("Holding details / status", style = MaterialTheme.typography.titleMedium)
    Choice("Holding to edit", holdings.map { it.id to it.name }, id, !busy) {
        id = it
        pendingArchive = null
        account = holdings.firstOrNull { h -> h.id == it }?.accountId.orEmpty()
        notes = holdings.firstOrNull { h -> h.id == it }?.notes.orEmpty()
        val chosen = holdings.firstOrNull { h -> h.id == it }
        specialistValues = if (chosen == null) emptyMap() else runCatching {
            NativeHoldingSpecialist.read(chosen.detailsJson, chosen.category, chosen.subcategory)
        }.getOrDefault(emptyMap())
        specialistError = ""
        location = chosen?.location.orEmpty()
        goalId = chosen?.goalId.orEmpty()
    }
    if (holding != null) {
        Text("${holding.name} • ${holding.personName} • ${holding.category} / ${holding.subcategory}",
            style = MaterialTheme.typography.titleMedium)
        Text("Recorded position: " +
            NativeBooksRepository.formatMinor(holding.quantityScaled, 8) +
            " units. All prior ledger event IDs and corrections remain immutable.")
        Text("Current custody: " +
            (accounts.firstOrNull { it.id == holding.accountId }?.custodian ?: "No account") +
            "; linked goal: " +
            (goals.firstOrNull { it.id == holding.goalId }?.name ?: "None"))
        if (holding.archived) Text("Closed to new financial events; reopen explicitly before a new entry.")
        Choice("Custody", listOf("" to "No account") + accounts.filter {
            it.personId == holding.personId }.map { it.id to it.custodian },
            account, !busy) { account = it }
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth().testTag("m27-edit-notes"))
        OutlinedTextField(location, { location = it }, label = { Text("Property/location (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("m27-edit-location"))
        SpecialistFields(holding.category, holding.subcategory, specialistValues) { key, value ->
            specialistValues = specialistValues + (key to value)
            specialistError = ""
        }
        if (specialistError.isNotEmpty())
            Text(specialistError, color = MaterialTheme.colorScheme.error)
        val explicitIncomeStart = specialistValues["incomeStart"]
        val incomePreview = if (explicitIncomeStart.isNullOrBlank()) null else runCatching {
            val details = NativeHoldingSpecialist.merge(
                holding.detailsJson, holding.category, holding.subcategory, specialistValues)
            NativeHoldingSpecialist.monthlyIncomeSchedule(
                details, explicitIncomeStart, LocalDate.now().toString())
        }.getOrNull()
        if (incomePreview != null) {
            Text("Unposted income schedule: ₹" +
                NativeBooksRepository.formatMinor(incomePreview.amountMinor, 4) +
                " monthly, " + incomePreview.scheduledThroughAsOf +
                " dates through today. Next: " + (incomePreview.nextDate ?: "none"))
            Text("This is a schedule preview, not received income or an automatic ledger event.")
        }
        Button(enabled = !busy, onClick = {
            val typed = runCatching {
                NativeHoldingSpecialist.merge(holding.detailsJson, holding.category,
                    holding.subcategory, specialistValues)
            }
            if (typed.isFailure) specialistError = "Invalid asset-specific details. No edits saved."
            else update(id, account.ifBlank { null }, notes.ifBlank { null },
                typed.getOrNull(), location.ifBlank { null })
        }) { Text("Save non-ledger metadata") }
        Choice("Linked goal", listOf("" to "No linked goal") +
            goals.filter { it.personId == null || it.personId == holding.personId }
                .map { it.id to it.name }, goalId, !busy) { goalId = it }
        Button(enabled = !busy, onClick = { assign(id, goalId.ifBlank { null }) }) {
            Text("Set linked goal")
        }
        if (pendingArchive == id && !holding.archived) {
            Text("Confirm archive for ${holding.name} (${holding.personName}): " +
                "only a zero-unit reconciled holding can close. " +
                "All original financial events and source history remain permanently recorded.",
                color = MaterialTheme.colorScheme.error)
            Button(enabled = !busy, onClick = {
                archive(id, true)
                pendingArchive = null
                // Do not discard the selected holding before SQLCipher confirms the write.
            }, modifier = Modifier.testTag("m27-archive-confirm")) { Text("Confirm archive with immutable history retained") }
            OutlinedButton(enabled = !busy, onClick = { pendingArchive = null }) {
                Text("Cancel archive")
            }
        } else OutlinedButton(enabled = !busy, onClick = {
            if (holding.archived) {
                archive(id, false)
                // Retain the selection if the repository rejects reopening.
            } else pendingArchive = id
        }, modifier = Modifier.testTag("m27-archive-action")) {
            Text(if (holding.archived) "Reopen archived holding" else "Review archive and close")
        }
    }
}

/** The owner explicitly reviews the loan status and due date before posting EMIs. */
@Composable
private fun NativeEmiForm(holdings: List<NativeBooksRepository.Holding>, busy: Boolean,
    onSettings: (String, String, Long, String?, Int) -> Unit,
    onCatchUp: (String, String) -> Unit) {
    if (holdings.isEmpty()) return
    var holdingId by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("PAYING") }
    var monthly by remember { mutableStateOf("") }
    var nextDue by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }
    var asOf by remember { mutableStateOf(LocalDate.now().toString()) }
    HorizontalDivider()
    Text("Loan repayments and EMI catch-up", style = MaterialTheme.typography.titleMedium)
    Text("Repayment events reduce recorded outstanding. The app does not invent interest.")
    Choice("Liability", holdings.map { it.id to it.name }, holdingId, !busy) {
        holdingId = it
    }
    Choice("Payment state", listOf("PAYING", "PAUSED", "STOPPED").map { it to it },
        status, !busy) { status = it }
    OutlinedTextField(monthly, { monthly = it }, label = { Text("EMI amount ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(nextDue, { nextDue = it },
        label = { Text("Next due date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(remaining, { remaining = it },
        label = { Text("Installments remaining") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && holdingId.isNotEmpty(), onClick = {
        val minor = runCatching { NativeBooksRepository.parseMinor(monthly, 4) }.getOrNull()
        val count = remaining.toIntOrNull()
        if (minor != null && count != null) {
            onSettings(holdingId, status, minor, nextDue.ifBlank { null }, count)
        }
    }) { Text("Save reviewed EMI schedule") }
    OutlinedTextField(asOf, { asOf = it },
        label = { Text("Catch up through YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && holdingId.isNotEmpty(), onClick = {
        onCatchUp(holdingId, asOf)
    }) { Text("Post only unpaid due installments") }
}


/**
 * CSV snapshot approval is never automatic. Selecting a file via Android SAF
 * invalidates the device/password session; a fresh two-step login is necessary
 * before these bytes can be inspected. The Activity holds bytes in RAM only.
 */
@Composable
private fun NativeCsvImportCard(email: String,
    imports: NativeStatementImportRepository,
    bytes: ByteArray?, filename: String, choose: () -> Unit, discard: () -> Unit,
    onCommitted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<NativeStatementImportRepository.Review?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(bytes) {
        preview = null
        message = ""
    }
    Text("Import an explicitly formatted CSV snapshot",
        style = MaterialTheme.typography.titleLarge)
    Text("For CSV exports with scaled-integer amounts and an exact owner PAN plus " +
        "ISIN or folio. This is not PDF CAS parsing. No email/name-based matching.")
    Text("Required columns: owner_pan,statement_date,isin,folio,quantity_scaled," +
        "unit_price_minor,current_value_minor", style = MaterialTheme.typography.bodySmall)
    if (bytes == null) {
        Button(onClick = choose) { Text("Choose CSV through Android files") }
        Text("File selection locks the app. Verify device and password again to review.")
    } else {
        Text("Private CSV file ready for review: ${filename.take(120)}")
        OutlinedButton(enabled = !busy, onClick = { preview = null; discard() }) {
            Text("Discard without importing")
        }
        Button(enabled = !busy, onClick = {
            busy = true
            message = ""
            preview = null
            scope.launch {
                try {
                    preview = withContext(Dispatchers.IO) { imports.inspect(email, bytes) }
                } catch (_: Exception) {
                    message = "Cannot verify owner, instrument mapping, or statement chronology. " +
                        "No data was imported."
                } finally { busy = false }
            }
        }) { Text("Verify ownership and preview adjustments") }
        preview?.let { plan ->
            if (plan.duplicate) {
                Text("These exact file bytes were already imported. No new events are required.")
            } else {
                Text("Statement date: ${plan.statementDate}")
                plan.matched.forEach { item ->
                    Text("${item.name}: statement " +
                        "${NativeBooksRepository.formatMinor(item.quantityScaled, 8)} " +
                        "units; book " +
                        "${NativeBooksRepository.formatMinor(item.storedQuantityScaled, 8)} " +
                        "units • ${item.action}")
                    if (item.action == NativeStatementImportRepository.Action.APPLY_SNAPSHOT)
                        Text("Difference ${NativeBooksRepository.formatMinor(item.delta, 8)} " +
                            "units; cost of added units remains unknown")
                }
                Text("Owner approval records source snapshots, not taxable buys or sells. " +
                    "Verify every row against the original statement.")
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val receipt = withContext(Dispatchers.IO) {
                                imports.approve(email, filename, bytes, plan, true)
                            }
                            message = if (receipt.duplicate) "Already imported."
                                else "Approved ${receipt.positions} statement positions and " +
                                    "${receipt.adjustments} non-trade adjustments."
                            preview = null
                            discard()
                            onCommitted()
                        } catch (_: Exception) {
                            message = "Approval denied: file, identity or ledger changed. " +
                                "No partial import was committed."
                        } finally { busy = false }
                    }
                }) { Text("Approve exact reviewed CSV snapshot") }
            }
        }
    }
    if (message.isNotEmpty()) Text(message)
}


/**
 * PDF parsing runs ONLY on-device through Chaquopy's frozen CAS entry.
 * Password and parsed positions live in ephemeral Compose state, not disk,
 * saved instance state, diagnostics or an HTTP sidecar.
 */
@Composable
private fun NativeCasPdfCard(email: String, imports: NativeStatementImportRepository,
    engine: NativeCasPdfEngine, pdf: ByteArray?, filename: String,
    choose: () -> Unit, discard: () -> Unit, onCommitted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmedPan by remember { mutableStateOf("") }
    var ownerApprovedReview by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf<NativeStatementImportRepository.Review?>(null) }
    var parsed by remember { mutableStateOf<NativeCasPdfEngine.Parsed?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(pdf) {
        password = ""
        confirmedPan = ""
        reviewed = null
        parsed = null
        ownerApprovedReview = false
        message = ""
    }
    Text("Import a real CAS PDF", style = MaterialTheme.typography.titleLarge)
    Text("On-device frozen CASPARSER for CAMS, KFintech, NSDL, CDSL and NPS. " +
        "Only exact existing PAN and instrument identity matches can be accepted.")
    if (pdf == null) {
        Button(onClick = choose) { Text("Select locked CAS PDF") }
        Text("Android file selection locks the vault. Re-authenticate to parse.")
    } else {
        Text("Selected: ${filename.take(120)}")
        OutlinedTextField(password, { password = it }, label = { Text("Statement PDF password") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirmedPan, { confirmedPan = it },
            label = { Text("Confirm full owner PAN separately from PDF password") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        Text("The frozen parser may infer a PAN from the PDF password. " +
            "Confirm the actual statement owner against your encrypted family records; " +
            "this is owner attestation, not independent issuer verification.")
        OutlinedButton(enabled = !busy, onClick = {
            reviewed = null; parsed = null; ownerApprovedReview = false; password = ""; confirmedPan = ""; discard()
        }) { Text("Discard PDF and password") }
        Button(enabled = !busy, onClick = {
            busy = true; message = ""; reviewed = null; parsed = null; ownerApprovedReview = false
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        engine.parse(pdf, password, filename, confirmedPan)
                    }
                    val review = withContext(Dispatchers.IO) {
                        imports.inspectSnapshot(email, result.snapshot, pdf)
                    }
                    parsed = result
                    reviewed = review
                } catch (_: Exception) {
                    message = "PDF parsing, total checks, identity matching or " +
                        "reconciliation failed. No records were imported."
                } finally { busy = false }
            }
        }) { Text("Parse locally and review statement") }
        val plan = reviewed
        val details = parsed
        if (plan != null && details != null) {
            Text("Issuer: ${details.issuer}; dated ${plan.statementDate}; " +
                "${details.positionCount} positions; ${details.warnings} parser warnings")
            if (plan.duplicate) {
                Text("Already imported from these exact PDF bytes.")
            } else {
                plan.matched.forEachIndexed { index, item ->
                    val custody = details.snapshot.positions[index].dematAccount
                    custody?.let {
                        fun mask(id: String): String = if (id.length > 4)
                            "****" + id.takeLast(4) else "****"
                        Text("Demat owner/account review: ${it.issuer}, " +
                            "DP ${mask(it.dpId)}, client ${mask(it.clientId)}")
                    }
                    Text("${item.name}: ${NativeBooksRepository.formatMinor(
                        item.storedQuantityScaled, 8)} → ${NativeBooksRepository.formatMinor(
                        item.quantityScaled, 8)} units (${item.action})")
                }
                Text("Do not approve if the source, owners, holdings, " +
                    "or totals do not match the PDF. Changes are snapshots, never purchases.")
                androidx.compose.material3.Checkbox(checked = ownerApprovedReview,
                    onCheckedChange = { ownerApprovedReview = it }, enabled = !busy)
                Text("I checked the statement owner, each issuer DP/client custody account, matched holding, units and totals. I explicitly approve these source snapshots.")
                Button(enabled = !busy && ownerApprovedReview, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            // Reparse at approval; stale altered bytes cannot be committed.
                            val fresh = withContext(Dispatchers.IO) {
                                engine.parse(pdf, password, filename, confirmedPan)
                            }
                            val receipt = withContext(Dispatchers.IO) {
                                imports.approveSnapshot(email, filename, pdf,
                                    fresh.snapshot, "CAS_PDF", plan, ownerApprovedReview)
                            }
                            message = if (receipt.duplicate) "Already imported."
                                else "Approved ${receipt.positions} CAS positions; " +
                                    "${receipt.adjustments} reconciled quantity changes."
                            reviewed = null; parsed = null; ownerApprovedReview = false; password = ""; confirmedPan = ""; discard()
                            onCommitted()
                        } catch (_: Exception) {
                            message = "Import refused: source or ledger changed, " +
                                "or the CAS parser is unavailable. No partial records saved."
                        } finally { busy = false }
                    }
                }) { Text("Approve verified CAS PDF snapshot") }
            }
        }
    }
    if (message.isNotBlank()) Text(message)
}


/**
 * Explicit owner review for cash coupon-only catch-up. A statement is NOT
 * presumed received merely because a rate and due date exist. No automatic
 * posting or changes to holding prices, quantities or compounded balances.
 */
@Composable
private fun NativeInterestPayoutForm(email: String,
    payouts: NativeInterestPayoutRepository,
    holdings: List<NativeBooksRepository.Holding>, parentBusy: Boolean,
    onCommitted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("SGB") }
    var principal by remember { mutableStateOf("") }
    var rateBps by remember { mutableStateOf("") }
    var acquired by remember { mutableStateOf("") }
    var maturity by remember { mutableStateOf("") }
    var asOf by remember { mutableStateOf(LocalDate.now().toString()) }
    var reviewed by remember { mutableStateOf<NativeInterestPayoutRepository.Review?>(null) }
    var message by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val busy = working || parentBusy
    LaunchedEffect(holding, scheme, principal, rateBps, acquired, maturity, asOf) {
        reviewed = null
    }
    HorizontalDivider()
    Text("Reviewed cash coupons — partial interest support",
        style = MaterialTheme.typography.titleMedium)
    Text("SGB, SCSS or POMIS only. Enter the actual contract principal, " +
        "annual rate and purchase date. These are not verified issuer " +
        "receipts; no historical rate or interest is guessed.")
    Choice("Interest holding", holdings.map { it.id to
        "${it.name} • ${it.personName}" }, holding, !busy) { holding = it }
    Choice("Coupon kind", listOf("SGB", "SCSS", "POMIS").map { it to it },
        scheme, !busy) { scheme = it }
    OutlinedTextField(principal, { principal = it },
        label = { Text("Owner-confirmed contract principal ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(rateBps, { rateBps = it },
        label = { Text("Contract annual rate (basis points, e.g. 250 = 2.5%)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(acquired, { acquired = it },
        label = { Text("Actual acquisition date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(maturity, { maturity = it },
        label = { Text("Maturity YYYY-MM-DD (optional)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(asOf, { asOf = it },
        label = { Text("Review due coupons through YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && holding.isNotBlank(), onClick = {
        message = ""
        reviewed = null
        val input = runCatching {
            NativeInterestPayoutPlanner.Input(
                NativeInterestPayoutPlanner.Kind.valueOf(scheme),
                NativeBooksRepository.parseMinor(principal, 4),
                requireNotNull(rateBps.toIntOrNull()),
                LocalDate.parse(acquired), LocalDate.parse(asOf),
                maturity.takeIf { it.isNotBlank() }?.let(LocalDate::parse))
        }.getOrNull()
        if (input == null) {
            message = "Enter exact valid principal, rate and dates."
        } else {
            working = true
            scope.launch {
                try {
                    reviewed = withContext(Dispatchers.IO) {
                        payouts.inspect(email, holding, input)
                    }
                } catch (_: Exception) {
                    message = "Cannot review: owner, contract or ledger evidence is " +
                        "incompatible. No interest was posted."
                } finally { working = false }
            }
        }
    }) { Text("Preview all due coupon dates and amounts") }
    reviewed?.let { plan ->
        Text("${plan.coupons.size} scheduled cash receipt(s) awaiting review")
        plan.coupons.forEach { coupon ->
            Text("${coupon.date} • ₹${NativeBooksRepository.formatMinor(
                coupon.amountMinor, 4)}")
        }
        if (plan.coupons.isEmpty()) {
            Text("No unpaid coupons in the reviewed window.")
        } else {
            Text("Approve only after independently confirming these payments; " +
                "this app has not verified bank credit or issuer statements.")
            Button(enabled = !busy, onClick = {
                working = true
                scope.launch {
                    try {
                        val receipt = withContext(Dispatchers.IO) {
                            payouts.approve(email, plan, true)
                        }
                        message = "Recorded ${receipt.posted} owner-reviewed " +
                            "immutable cash coupon(s)."
                        reviewed = null
                        onCommitted()
                    } catch (_: Exception) {
                        message = "Approval refused: contract or encrypted " +
                            "history changed. No partial coupon batch committed."
                    } finally { working = false }
                }
            }) { Text("Approve and record these cash receipts") }
        }
    }
    if (message.isNotBlank()) Text(message)
}

/**
 * Settings are a partial section: technical diagnostics privacy controls are
 * functional; remaining theme/market/provenance/account preferences are OPEN.
 */
@Composable
private fun NativePrivateDiagnosticsSettings(email: String,
    diagnostics: NativePrivateDiagnosticsRepository) {
    val scope = rememberCoroutineScope()
    var optedIn by remember(email) { mutableStateOf(false) }
    var loaded by remember(email) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(email) {
        report = ""
        try {
            optedIn = withContext(Dispatchers.IO) { diagnostics.enabled(email) }
            loaded = true
        } catch (_: Exception) {
            loaded = false
            message = "Diagnostics settings inaccessible. Lock and unlock again."
        }
    }
    Text("Privacy and technical diagnostics",
        style = MaterialTheme.typography.titleLarge)
    Text("Opt-in only, entirely local. No financial values, personal identity, " +
        "PDFs, PAN, emails, credentials or raw logs are collected or uploaded.")
    if (!loaded) Text("Diagnostics require an unlocked encrypted vault.")
    else {
        Text(if (optedIn) "Technical diagnostics: enabled" else
            "Technical diagnostics: disabled (default)")
        Button(enabled = !busy && !optedIn, onClick = {
            busy = true; scope.launch {
                try {
                    withContext(Dispatchers.IO) { diagnostics.setEnabled(email, true) }
                    optedIn = true
                    message = "Local technical checks enabled. Nothing is uploaded."
                } catch (_: Exception) {
                    message = "Consent was not changed. Reauthenticate."
                } finally { busy = false }
            }
        }) { Text("Opt in to on-device technical checks") }
        OutlinedButton(enabled = !busy && optedIn, onClick = {
            busy = true; report = ""
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        diagnostics.localReport(email)
                    }
                    report = result?.redactedText().orEmpty()
                    if (report.isEmpty()) message = "Diagnostics consent is disabled."
                } catch (_: Exception) {
                    message = "Technical health unavailable. No diagnostic data exported."
                } finally { busy = false }
            }
        }) { Text("View sanitized health locally") }
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { diagnostics.setEnabled(email, false) }
                    optedIn = false
                    report = ""
                    message = "Diagnostics disabled; encrypted opt-in marker cleared. " +
                        "No diagnostic logs are retained."
                } catch (_: Exception) {
                    message = "Could not clear consent. Reauthenticate."
                } finally { busy = false }
            }
        }) { Text("Opt out and clear local diagnostics") }
        if (report.isNotEmpty()) Text(report, style = MaterialTheme.typography.bodySmall)
        if (message.isNotEmpty()) Text(message)
        Text("Other appearance, family, market and account settings " +
            "are not yet implemented in this native section.")
    }
}
