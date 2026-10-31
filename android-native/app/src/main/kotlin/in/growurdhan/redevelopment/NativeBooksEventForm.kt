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
        if (cursor.sourceBacked || cursor.kind == "SNAPSHOT_ADJUSTMENT") return null
        val visited = mutableSetOf<String>()
        while (cursor.kind == "CORRECTION") {
            if (cursor.sourceBacked) return null
            if (!visited.add(cursor.id)) return null
            val previousId = cursor.supersedes ?: return null
            cursor = byId[previousId] ?: return null
        }
        return cursor.kind.takeUnless { cursor.sourceBacked || it == "SNAPSHOT_ADJUSTMENT" }
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
            history.filter { !it.sourceBacked && it.kind != "SNAPSHOT_ADJUSTMENT" }
                .map { it.id to "${it.date} • ${it.kind} • ${it.id}" },
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
            require(p >= 0L && fee >= 0L && m >= 0L)
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
        val cashReview = runCatching {
            NativeTradeCashReview.preview(effectiveKind ?: pending.kind,
                pending.quantityScaled, pending.unitPriceMinor,
                pending.amountMinor, pending.chargesMinor)
        }.getOrNull()
        if (cashReview != null) {
            Text("Price × units (computed gross, not proof of payment): ₹" +
                NativeBooksRepository.formatMinor(cashReview.computedGrossMinor, 4),
                modifier = Modifier.testTag("m27-trade-cash-review"))
            Text("Recorded charges: ₹" +
                NativeBooksRepository.formatMinor(cashReview.chargesMinor, 4))
            // Cross-module model properties cannot be smart-cast by Compose Kotlin:
            // snapshot the optional Long before branching.
            val recordedCash = cashReview.recordedSignedCashMinor
            if (recordedCash == null)
                Text("No nonzero transaction total recorded. A price-based cash flow is " +
                    "only a calculation fallback, not an attested receipt.",
                    color = MaterialTheme.colorScheme.error)
            else Text("Signed cash after charges from your recorded total: ₹" +
                NativeBooksRepository.formatMinor(recordedCash, 4))
            val difference = cashReview.grossDifferenceMinor
            if (difference != null && difference != 0L)
                Text("Recorded total differs from price × units by ₹" +
                    NativeBooksRepository.formatMinor(difference, 4) +
                    ". Verify your transaction evidence before confirming.",
                    color = MaterialTheme.colorScheme.error)
        }
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

