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
internal fun NativeCsvImportCard(email: String,
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
internal fun NativeCasPdfCard(email: String, imports: NativeStatementImportRepository,
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
