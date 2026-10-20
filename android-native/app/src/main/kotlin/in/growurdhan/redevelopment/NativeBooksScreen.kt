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
internal fun NativeBooksScreen(email: String, books: NativeBooksRepository, onLock: () -> Unit,
    onReplaceRecovery: () -> Unit) {
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
    Button(enabled = !busy && owner.isNotBlank() && custodian.isNotBlank(), onClick = {
        save(NativeBooksRepository.NewCustody(owner, type, custodian,
            masked.ifBlank { null }, nominee = nominee.ifBlank { null }))
        custodian = ""; masked = ""; nominee = ""
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
    Button(enabled = !busy && owner.isNotBlank() && name.isNotBlank(), onClick = {
        val qty = runCatching { NativeBooksRepository.parseMinor(quantity, 8) }.getOrNull()
        val minor = runCatching { NativeBooksRepository.parseMinor(price, 4) }.getOrNull()
        if (qty != null && minor != null) {
            save(NativeBooksRepository.NewHolding(owner, account.ifBlank { null }, name,
                category, sub, qty, minor, date, knownCost == "Known",
                notes = notes.ifBlank { null }))
            name = ""; notes = ""; quantity = "0"; price = "0"
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
    Choice("Holding", holdings.map { it.id to "${it.name} • ${it.personName}" },
        holding, !busy) { holding = it }
    Choice("Event kind", listOf("BUY", "SELL", "DIVIDEND", "INTEREST", "BONUS", "SPLIT",
        "REPAYMENT", "OPENING_BALANCE", "VALUATION_UPDATE").map { it to it }, kind,
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
    Button(enabled = !busy && holding.isNotBlank(), onClick = {
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
