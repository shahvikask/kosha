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
internal fun PersonEditForm(people: List<NativeBooksRepository.Person>, busy: Boolean,
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
internal fun CustodyEditForm(accounts: List<NativeBooksRepository.Custody>, busy: Boolean,
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
internal fun GoalEditForm(goals: List<NativeBooksRepository.Goal>, busy: Boolean,
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
    // An async SQLCipher failure must leave the owner's confirmation on screen.
    // Clear it only once the refreshed encrypted snapshot confirms closure.
    LaunchedEffect(id, holding?.archived) {
        if (pendingArchive == id && holding?.archived == true) pendingArchive = null
    }
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
            else update(id, account.ifBlank { null }, notes,
                typed.getOrNull(), location)
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
                // Selection and confirmation survive until the committed snapshot arrives.
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
internal fun NativeEmiForm(holdings: List<NativeBooksRepository.Holding>, busy: Boolean,
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
