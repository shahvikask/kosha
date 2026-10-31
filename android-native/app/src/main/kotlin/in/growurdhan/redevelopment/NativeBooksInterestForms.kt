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
internal fun NativeInterestPayoutForm(email: String,
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
internal fun NativePrivateDiagnosticsSettings(email: String,
    diagnostics: NativePrivateDiagnosticsRepository) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var optedIn by remember(email) { mutableStateOf(false) }
    var loaded by remember(email) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var copiedReport by remember(email) { mutableStateOf("") }
    val clearOwnedCopy = {
        if (copiedReport.isNotEmpty() && clipboard.getText()?.text == copiedReport)
            clipboard.setText(AnnotatedString(""))
        copiedReport = ""
    }
    LaunchedEffect(email) {
        report = ""
        copiedReport = ""
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
                    clearOwnedCopy()
                    message = "Diagnostics disabled; encrypted opt-in marker cleared. " +
                        "No diagnostic logs are retained."
                } catch (_: Exception) {
                    message = "Could not clear consent. Reauthenticate."
                } finally { busy = false }
            }
        }) { Text("Opt out and clear local diagnostics") }
        if (report.isNotEmpty()) {
            Text("Only the redacted lines below are copied. Other apps may read " +
                "clipboard contents; clear clipboard after sending to support.")
            Text(report, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("m31-redacted-report"))
            OutlinedButton(enabled = optedIn && !busy, onClick = {
                // Owner-initiated copy only, never raw logs or financial records.
                clipboard.setText(AnnotatedString(report))
                copiedReport = report
                message = "Redacted support text copied by explicit request; no file exported."
            }, modifier = Modifier.testTag("m31-support-copy")) {
                Text("Copy sanitized support text manually")
            }
            OutlinedButton(onClick = {
                report = ""
                clearOwnedCopy()
                message = "Support preview and its unchanged clipboard copy cleared."
            },
                modifier = Modifier.testTag("m31-support-clear")) {
                Text("Clear local support preview")
            }
        }
        if (copiedReport.isNotEmpty()) {
            OutlinedButton(onClick = {
                clearOwnedCopy()
                message = "Owned clipboard copy cleared if unchanged."
            }, modifier = Modifier.testTag("m31-support-delete-copy")) {
                Text("Clear copied support text")
            }
        }
        if (message.isNotEmpty()) Text(message)
        Text("No technical event log is retained: only encrypted consent is saved. " +
            "Opt-out deletes that marker. Financial data export remains unavailable.")
    }
}

