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

/** Bounded lazy list; stable IDs survive owner lens, search and sort changes. */
@Composable
internal fun NativeHoldingsLazyList(
    holdings: List<NativeBooksRepository.Holding>,
    content: @Composable (NativeBooksRepository.Holding) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)
        .testTag("m27-holdings-list")) {
        items(items = holdings, key = { it.id }) { holding ->
            content(holding)
        }
    }
}

@Composable
internal fun Choice(label: String, choices: List<Pair<String, String>>,
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
internal fun OwnerPicker(people: List<NativeBooksRepository.Person>, id: String,
    onChange: (String) -> Unit) =
    Choice("Member lens", listOf("" to "Whole household") +
        people.map { it.id to it.name }, id, onSelect = onChange)

@Composable
internal fun CategoryPicker(value: String, set: (String) -> Unit) =
    Choice("Category", listOf("All" to "All") +
        NativeAssetTaxonomy.subcategories.keys.map { it to it.replace("_", " ") },
        value, onSelect = set)

@Composable
internal fun PersonForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
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
internal fun CustodyForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
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
internal fun GoalForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
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
internal fun SpecialistFields(category: String, subcategory: String,
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

