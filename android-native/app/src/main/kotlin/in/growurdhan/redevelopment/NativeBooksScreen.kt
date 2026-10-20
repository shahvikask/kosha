package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.model.NativeAssetTaxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Real SQLCipher-backed owner-lens navigation. No example balances or sample records.
 * Financial values are rendered only while the parent Activity has an admitted session.
 */
@Composable
internal fun NativeBooksScreen(email: String, books: NativeBooksRepository,
    imports: NativeStatementImportRepository, pendingCsv: ByteArray?,
    pendingCsvFilename: String, onSelectCsv: () -> Unit, onDiscardCsv: () -> Unit,
    onLock: () -> Unit, onReplaceRecovery: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeBooksRepository.Snapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Home") }
    var ownerFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var historyId by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<NativeBooksRepository.EventRow>>(emptyList()) }

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
            } catch (_: Exception) {
                error = "Cannot save. The vault may have locked or this record is invalid. No partial edit was committed."
            } finally { busy = false }
        }
    }
    LaunchedEffect(email) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLock) { Text("Lock vault") }
                OutlinedButton(onClick = onReplaceRecovery) { Text("Recovery code") }
            }
            val data = snapshot
            if (data == null) {
                Text(if (error.isEmpty()) "Opening encrypted records…" else error)
            } else {
                val active = data.people.filter { it.active }
                OwnerPicker(active, ownerFilter) { ownerFilter = it }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Home", "Holdings", "Family").forEach { label ->
                        OutlinedButton(onClick = { tab = label }) { Text(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Accounts", "Goals", "Records").forEach { label ->
                        OutlinedButton(onClick = { tab = label }) { Text(label) }
                    }
                }
                OutlinedButton(onClick = { tab = "Documents" }) { Text("Documents and imports") }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                when (tab) {
                    "Home" -> {
                        val selected = data.holdings.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                        val warnings = selected.count { it.unknownCost || it.needsReview || it.priceDate == null }
                        Text("${selected.size} holdings • ${active.size} active members",
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
                        active.forEach { m ->
                            Text("${m.name} • ${m.relation} • ${m.kind}" +
                                (if (m.isPrimary) " (Primary)" else "") +
                                (m.maskedPan?.let { " • $it" } ?: ""))
                        }
                        PersonForm(active, busy) { form ->
                            persist { books.createPerson(email, form) }
                        }
                        PersonEditForm(active, busy,
                            update = { id, form -> persist { books.updatePerson(email, id, form) } },
                            archive = { id -> persist { books.archivePerson(email, id) } })
                    }
                    "Accounts" -> {
                        Text("Custody accounts", style = MaterialTheme.typography.titleLarge)
                        data.custody.filter { ownerFilter.isEmpty() || it.personId == ownerFilter }
                            .forEach { a ->
                                val name = active.firstOrNull { it.id == a.personId }?.name ?: "Archived member"
                                Text("${a.custodian} • ${a.type} • $name" +
                                    (a.maskedReference?.let { " • $it" } ?: ""))
                            }
                        CustodyForm(active, busy) { form ->
                            persist { books.createCustody(email, form) }
                        }
                        CustodyEditForm(data.custody.filter { ownerFilter.isEmpty() ||
                            it.personId == ownerFilter }, busy,
                            update = { id, form -> persist { books.updateCustody(email, id, form) } },
                            close = { a -> persist {
                                books.closeCustody(email, a.personId, a.id, LocalDate.now().toString())
                            } })
                    }
                    "Goals" -> {
                        Text("Family goals", style = MaterialTheme.typography.titleLarge)
                        data.goals.filter { ownerFilter.isEmpty() || it.personId == null ||
                            it.personId == ownerFilter }.forEach { g ->
                            Text("${g.name} • ${g.targetMinor?.let {
                                "₹" + NativeBooksRepository.formatMinor(it, 4)
                            } ?: "Target not recorded"} • ${g.targetDate.orEmpty()}")
                        }
                        GoalForm(active, busy) { id, name, minor, day, priority ->
                            persist { books.createGoal(email, id, name, minor, day, priority) }
                        }
                        GoalEditForm(data.goals.filter { ownerFilter.isEmpty() ||
                            it.personId == ownerFilter || it.personId == null }, busy,
                            update = { goal, name, amount, day, priority -> persist {
                                books.updateGoal(email, goal.id, goal.personId, name, amount, day, priority)
                            } },
                            remove = { id -> persist { books.removeUnlinkedGoal(email, id) } })
                    }
                    "Holdings" -> {
                        Text("Holdings", style = MaterialTheme.typography.titleLarge)
                        CategoryPicker(categoryFilter) { categoryFilter = it }
                        OutlinedTextField(search, { search = it }, label = { Text("Search holdings") },
                            modifier = Modifier.fillMaxWidth())
                        val list = data.holdings.filter {
                            (ownerFilter.isEmpty() || it.personId == ownerFilter) &&
                                (categoryFilter == "All" || it.category == categoryFilter) &&
                                (it.name.contains(search, true) || it.personName.contains(search, true))
                        }
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
                                historyId = h.id
                                busy = true
                                scope.launch {
                                    try {
                                        history = withContext(Dispatchers.IO) { books.history(email, h.id) }
                                    } catch (_: Exception) {
                                        error = "Event history unavailable. Verify the vault again."
                                    } finally { busy = false }
                                }
                            }) { Text("View audit history") }
                        }
                        if (historyId.isNotEmpty()) {
                            Text("Append-only event history", style = MaterialTheme.typography.titleMedium)
                            history.forEach { e ->
                                Text("${e.date} • ${e.kind} • ${NativeBooksRepository.formatMinor(e.quantityScaled, 8)} " +
                                    "units • ${e.id.take(8)}")
                            }
                            TextButton(onClick = { historyId = ""; history = emptyList() }) {
                                Text("Close history")
                            }
                        }
                        HoldingForm(active, data.custody, busy) { form ->
                            persist { books.createHolding(email, form) }
                        }
                        HoldingEditForm(list, data.custody, data.goals, busy,
                            update = { id, account, notes, details, location -> persist {
                                books.updateHoldingMetadata(email, id, account, notes, details, location)
                            } },
                            assign = { id, goal -> persist { books.assignGoal(email, id, goal) } },
                            archive = { id, value -> persist { books.setHoldingArchived(email, id, value) } })
                    }
                    "Documents" -> {
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
                    "Records" -> {
                        Text("Money events and manual prices", style = MaterialTheme.typography.titleLarge)
                        EventForm(data.holdings.filter { ownerFilter.isEmpty() ||
                            it.personId == ownerFilter }, busy,
                            add = { form -> persist { books.appendUserEvent(email, form) } },
                            price = { id, value, day ->
                                persist { books.recordManualPrice(email, id, value, day) }
                            })
                        Text("Statement adjustments cannot be created as manual buys or sells.",
                            style = MaterialTheme.typography.bodySmall)
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
    OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
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
    save: (String?, String, Long, String, String) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    Text("Create goal", style = MaterialTheme.typography.titleMedium)
    Choice("Goal owner", listOf("" to "Household") +
        people.map { it.id to it.name }, owner, !busy) { owner = it }
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
                save(owner.ifBlank { null }, name, minor, day, priority)
                name = ""; target = ""; day = ""
            }
        }) { Text("Save goal") }
}

@Composable
private fun HoldingForm(people: List<NativeBooksRepository.Person>,
    custody: List<NativeBooksRepository.Custody>, busy: Boolean,
    save: (NativeBooksRepository.NewHolding) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUITY") }
    var sub by remember { mutableStateOf("Other") }
    var quantity by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("0") }
    var knownCost by remember { mutableStateOf("Known") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
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
    var specialistJson by remember { mutableStateOf("") }
    Text("New holding — review fields before saving", style = MaterialTheme.typography.titleMedium)
    Choice("Owner", people.map { it.id to it.name }, owner, !busy) { owner = it; account = "" }
    Choice("Custody account", listOf("" to "No custody account") +
        custody.filter { it.personId == owner }.map { it.id to it.custodian },
        account, !busy) { account = it }
    Choice("Asset category", NativeAssetTaxonomy.subcategories.keys.map { it to it }, category,
        !busy) {
        category = it
        sub = NativeAssetTaxonomy.subcategories.getValue(it).first()
    }
    Choice("Subcategory", NativeAssetTaxonomy.subcategories.getValue(category).map { it to it },
        sub, !busy) { sub = it }
    OutlinedTextField(name, { name = it }, label = { Text("Instrument / holding name") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(quantity, { quantity = it }, label = { Text("Opening units (may be 0)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(price, { price = it }, label = { Text("Price / unit ₹ (last known/manual)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(date, { date = it }, label = { Text("Acquisition date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    Choice("Opening cost", listOf("Known" to "Known", "Unknown" to "Unknown"), knownCost,
        !busy) { knownCost = it }
    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(isin, { isin = it }, label = { Text("ISIN (optional, 12 characters)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(openingPrice, { openingPrice = it },
        label = { Text("Opening cost per unit ₹ (optional; distinct from current price)") },
        modifier = Modifier.fillMaxWidth())
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
    OutlinedTextField(specialistJson, { specialistJson = it },
        label = { Text("Additional specialist details JSON") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && owner.isNotBlank() && name.isNotBlank(), onClick = {
        val qty = runCatching { NativeBooksRepository.parseMinor(quantity, 8) }.getOrNull()
        val minor = runCatching { NativeBooksRepository.parseMinor(price, 4) }.getOrNull()
        val opened = if (openingPrice.isBlank()) minor else runCatching {
            NativeBooksRepository.parseMinor(openingPrice, 4)
        }.getOrNull()
        val emi = if (emiMinor.isBlank()) null else runCatching {
            NativeBooksRepository.parseMinor(emiMinor, 4)
        }.getOrNull()
        val coupon = couponBps.toIntOrNull()
        val interest = interestBps.toIntOrNull()
        val count = emiCount.toIntOrNull()
        if (qty != null && minor != null && opened != null &&
            (emiMinor.isBlank() || emi != null) &&
            (couponBps.isBlank() || coupon != null) &&
            (interestBps.isBlank() || interest != null) &&
            (emiCount.isBlank() || count != null)) {
            save(NativeBooksRepository.NewHolding(owner, account.ifBlank { null }, name,
                category, sub, qty, minor, date, knownCost == "Known",
                isin = isin.ifBlank { null }, notes = notes.ifBlank { null },
                detailsJson = specialistJson.ifBlank { null },
                openingUnitPriceMinor = opened,
                institutionName = institution.ifBlank { null },
                accountNumberMasked = accountMasked.ifBlank { null },
                maturityDate = maturity.ifBlank { null },
                couponRateBps = coupon, interestRateBps = interest,
                monthlyEmiMinor = emi, emiStatus = emiState.ifBlank { null },
                remainingEmiCount = count, location = location.ifBlank { null }))
            name = ""; notes = ""; quantity = "0"; price = "0"; openingPrice = ""
        }
    }) { Text("Review and save holding") }
}

@Composable
private fun EventForm(holdings: List<NativeBooksRepository.Holding>, busy: Boolean,
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
    Choice("Holding", holdings.map { it.id to "${it.name} • ${it.personName}" },
        holding, !busy) { holding = it }
    Choice("Event kind", listOf("BUY", "SELL", "DIVIDEND", "INTEREST", "BONUS", "SPLIT",
        "REPAYMENT", "OPENING_BALANCE", "VALUATION_UPDATE", "CORRECTION").map { it to it }, kind,
        !busy) { kind = it }
    OutlinedTextField(day, { day = it }, label = { Text("Trade date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(units, { units = it }, label = { Text("Units (split: ratio numerator)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(unitPrice, { unitPrice = it },
        label = { Text(if (kind == "SPLIT") "Split ratio denominator (integer)" else "Unit price ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(amount, { amount = it }, label = { Text("Total amount ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(charges, { charges = it }, label = { Text("Charges ₹") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(note, { note = it }, label = { Text("Source or notes") },
        modifier = Modifier.fillMaxWidth())
    if (kind == "CORRECTION") {
        OutlinedTextField(correctionId, { correctionId = it },
            label = { Text("Exact superseded event ID (from audit history)") },
            modifier = Modifier.fillMaxWidth())
        Text("A correction is a new immutable event. It inherits the original event kind.")
    }
    Button(enabled = !busy && holding.isNotBlank() &&
        (kind != "CORRECTION" || correctionId.isNotBlank()), onClick = {
        val q = runCatching {
            if (kind == "SPLIT") units.toLong() else NativeBooksRepository.parseMinor(units, 8)
        }.getOrNull()
        val p = runCatching {
            if (kind == "SPLIT") unitPrice.toLong()
            else NativeBooksRepository.parseMinor(unitPrice, 4)
        }.getOrNull()
        val m = runCatching { NativeBooksRepository.parseMinor(amount, 4) }.getOrNull()
        val fee = runCatching { NativeBooksRepository.parseMinor(charges, 4) }.getOrNull()
        if (q != null && p != null && m != null && fee != null) {
            add(NativeBooksRepository.NewEvent(holding, kind, day, q, p, m, fee,
                supersedes = if (kind == "CORRECTION") correctionId.trim() else null,
                notes = note.ifBlank { null }))
            note = ""
        }
    }) { Text("Append money event") }
    HorizontalDivider()
    Text("Record manual dated price", style = MaterialTheme.typography.titleMedium)
    Button(enabled = !busy && holding.isNotBlank(), onClick = {
        val p = runCatching { NativeBooksRepository.parseMinor(unitPrice, 4) }.getOrNull()
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
private fun HoldingEditForm(holdings: List<NativeBooksRepository.Holding>,
    accounts: List<NativeBooksRepository.Custody>, goals: List<NativeBooksRepository.Goal>,
    busy: Boolean, update: (String, String?, String?, String?, String?) -> Unit,
    assign: (String, String?) -> Unit, archive: (String, Boolean) -> Unit) {
    var id by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var specialistJson by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var goalId by remember { mutableStateOf("") }
    val holding = holdings.firstOrNull { it.id == id }
    Text("Holding details / status", style = MaterialTheme.typography.titleMedium)
    Choice("Holding to edit", holdings.map { it.id to it.name }, id, !busy) {
        id = it
        account = holdings.firstOrNull { h -> h.id == it }?.accountId.orEmpty()
        notes = holdings.firstOrNull { h -> h.id == it }?.notes.orEmpty()
        specialistJson = ""
        location = ""
        goalId = ""
    }
    if (holding != null) {
        Choice("Custody", listOf("" to "No account") + accounts.filter {
            it.personId == holding.personId }.map { it.id to it.custodian },
            account, !busy) { account = it }
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(location, { location = it }, label = { Text("Property/location (optional)") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(specialistJson, { specialistJson = it },
            label = { Text("Specialist metadata JSON (leave empty to preserve)") },
            modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = {
            update(id, account.ifBlank { null }, notes.ifBlank { null },
                specialistJson.ifBlank { null }, location.ifBlank { null })
        }) { Text("Save non-ledger metadata") }
        Choice("Linked goal", listOf("" to "No linked goal") +
            goals.filter { it.personId == null || it.personId == holding.personId }
                .map { it.id to it.name }, goalId, !busy) { goalId = it }
        Button(enabled = !busy, onClick = { assign(id, goalId.ifBlank { null }) }) {
            Text("Set linked goal")
        }
        OutlinedButton(enabled = !busy, onClick = {
            archive(id, !holding.archived)
            id = ""
        }) { Text(if (holding.archived) "Reopen archived holding" else "Archive only if quantity is zero") }
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
