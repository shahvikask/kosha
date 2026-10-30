package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativeE09Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** E09 same authenticated owner lens as Goals, no cached plaintext financial data. */
@Composable
internal fun NativeE09GoalProgressCard(email: String, books: NativeBooksRepository,
    memberId: String, holdings: List<NativeBooksRepository.Holding>) {
    var state by remember(email, memberId) {
        mutableStateOf<List<NativeE09Analytics.GoalProgress>?>(null)
    }
    var failed by remember(email, memberId) { mutableStateOf(false) }
    LaunchedEffect(email, memberId, holdings) {
        state = null
        failed = false
        try {
            state = withContext(Dispatchers.IO) {
                books.recordedGoalProgress(email, memberId)
            }
        } catch (_: Exception) { failed = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded goal progress", style = MaterialTheme.typography.titleLarge)
        Text("Only explicitly linked current holdings with verified dated recorded " +
            "prices count; unknown or partial valuations never become zero.")
        when {
            failed -> Text("Goal progress unavailable. Unlock and reconcile your books.",
                modifier = Modifier.testTag("e09-goal-error"))
            state == null -> Text("Loading encrypted goal evidence…")
            state!!.isEmpty() -> Text("No goals in the selected owner view.",
                modifier = Modifier.testTag("e09-goal-empty"))
            else -> state!!.forEach { goal ->
                Text(goal.name + ": " + (goal.basisPoints?.let {
                    "$it / 10,000 basis points of recorded target"
                } ?: "N/A — incomplete or unpriced linked holdings") +
                    " · linked ${goal.linkedHoldingCount}, excluded ${goal.excludedLinkedHoldings}" +
                    (goal.completeValuedMinor?.let {
                        " · dated value ₹" + BigDecimal(it, 4).toPlainString()
                    } ?: ""),
                    modifier = Modifier.testTag("e09-goal-${goal.id}"))
            }
        }
    }
}

/** Read-only household or editable member-specific, strictly self-reported cash. */
@Composable
internal fun NativeE09ManualCashCard(email: String, books: NativeBooksRepository,
    memberId: String) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var entries by remember(email, memberId) {
        mutableStateOf<List<NativeE09Analytics.CashEntry>?>(null)
    }
    var failed by remember(email, memberId) { mutableStateOf(false) }
    var revision by remember(email, memberId) { mutableStateOf(0) }
    var saving by remember(email, memberId) { mutableStateOf(false) }
    var saveError by remember(email, memberId) { mutableStateOf(false) }
    var kind by remember(email, memberId) {
        mutableStateOf(NativeE09Analytics.CashKind.CONTRIBUTION)
    }
    var day by remember(email, memberId) {
        mutableStateOf(java.time.LocalDate.now().toString())
    }
    var amount by remember(email, memberId) { mutableStateOf("") }
    var note by remember(email, memberId) { mutableStateOf("") }
    LaunchedEffect(email, memberId, revision) {
        entries = null
        failed = false
        try {
            entries = withContext(Dispatchers.IO) { books.recordedManualCash(email, memberId) }
        } catch (_: Exception) { failed = true }
    }
    fun money(value: java.math.BigInteger) = "₹" + BigDecimal(value, 4).toPlainString()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded manual contributions and expenses", style = MaterialTheme.typography.titleLarge)
        Text("Self-reported cash annotations only. No bank verification, tax claim, " +
            "holding-ledger posting or buy/sell inference.")
        when {
            failed -> Text("Cash series unavailable: unlock/review encrypted records.",
                modifier = Modifier.testTag("e09-cash-error"))
            entries == null -> Text("Loading encrypted manual cash evidence…")
            entries!!.isEmpty() -> Text("No cash entries recorded; not proof of zero activity.",
                modifier = Modifier.testTag("e09-cash-empty"))
            else -> {
                for (year in NativeE09Analytics.manualCashYears(entries!!, memberId)) {
                Text("Calendar ${year.year}: contributions ${money(year.contributionMinor)}; " +
                    "expenses ${money(year.expenseMinor)} (self-reported)",
                    modifier = Modifier.testTag("e09-cash-year-${year.year}"))
                }
            }
        }
        if (memberId.isEmpty()) {
            Text("Select one member to add manual cash records; household is read-only.",
                modifier = Modifier.testTag("e09-cash-scope"))
        } else {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in NativeE09Analytics.CashKind.values()) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { kind = option }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("e09-cash-kind-${option.name}")) {
                        Text(if (option == kind) "✓ ${option.name}" else option.name)
                    }
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = day, onValueChange = { day = it },
                label = { Text("Date YYYY-MM-DD") }, singleLine = true,
                enabled = !saving, modifier = Modifier.testTag("e09-cash-date"))
            androidx.compose.material3.OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount INR, exact") }, singleLine = true,
                enabled = !saving, modifier = Modifier.testTag("e09-cash-amount"))
            androidx.compose.material3.OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Optional manual note") }, enabled = !saving,
                modifier = Modifier.testTag("e09-cash-note"))
            androidx.compose.material3.Button(
                enabled = !saving && !failed,
                modifier = Modifier.heightIn(min = 48.dp).testTag("e09-cash-save"),
                onClick = {
                    if (saving) return@Button
                    saving = true
                    saveError = false
                    scope.launch {
                        try {
                            val scaled = NativeBooksRepository.parseMinor(amount, 4)
                            withContext(Dispatchers.IO) {
                                books.recordManualCash(email, memberId, kind, day, scaled, note)
                            }
                            amount = ""
                            note = ""
                            revision++
                        } catch (_: Exception) { saveError = true
                        } finally { saving = false }
                    }
                }) { Text(if (saving) "Saving…" else "Record manual cash entry") }
            if (saveError) Text("Entry rejected. Review amount/date/owner or unlock vault.",
                modifier = Modifier.testTag("e09-cash-save-error"))
        }
    }
}
