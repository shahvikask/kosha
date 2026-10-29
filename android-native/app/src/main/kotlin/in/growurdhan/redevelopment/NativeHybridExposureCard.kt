package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** P24 review: subset of hybrid funds only, never full asset allocation. */
@Composable
internal fun NativeHybridExposureCard(email: String, books: NativeBooksRepository,
    holdings: List<NativeBooksRepository.Holding>, ownerFilter: String) {
    val scope = rememberCoroutineScope()
    val eligible = holdings.filter { it.category == "MUTUAL_FUND" &&
        it.subcategory == "Hybrid fund" && !it.archived &&
        (ownerFilter.isEmpty() || it.personId == ownerFilter) }
    var reviews by remember(email, ownerFilter) {
        mutableStateOf<List<NativeHybridExposureReview.Record>>(emptyList())
    }
    var loaded by remember(email, ownerFilter) { mutableStateOf(false) }
    var busy by remember(email, ownerFilter) { mutableStateOf(false) }
    var error by remember(email, ownerFilter) { mutableStateOf("") }
    var selectedId by remember(email, ownerFilter) { mutableStateOf("") }
    var menu by remember(email, ownerFilter) { mutableStateOf(false) }
    var split by remember(email, ownerFilter) { mutableStateOf("") }
    var source by remember(email, ownerFilter) { mutableStateOf("") }
    var asOf by remember(email, ownerFilter) { mutableStateOf("") }
    var estimated by remember(email, ownerFilter) { mutableStateOf(true) }

    LaunchedEffect(email, ownerFilter, holdings) {
        loaded = false
        try {
            reviews = withContext(Dispatchers.IO) {
                books.hybridExposureReviews(email, ownerFilter)
            }
            loaded = true
        } catch (_: Exception) {
            error = "Reviewed exposure cannot be loaded. Reauthenticate; no values shown."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reviewed hybrid look-through", style = MaterialTheme.typography.titleLarge)
        Text("Only currently owned hybrid mutual funds are eligible. This is NOT " +
            "complete household allocation. Source labels are owner-provided, " +
            "not independently issuer-verified; no financial events are created.")
        if (!loaded) {
            Text("Encrypted review data unavailable; no allocation is shown.")
        } else {
            val visibleIds = eligible.map { it.id }.toSet()
            val visibleReviews = reviews.filter { it.holdingId in visibleIds }
            val report = runCatching {
                NativeHybridExposureReview.report(holdings, visibleReviews,
                    ownerFilter, LocalDate.now())
            }.getOrNull()
            if (report == null) {
                Text("Exposure reconciliation failed: no totals published.",
                    modifier = Modifier.testTag("m29-exposure-rejected"))
            } else {
                val value = report.summary.knownValueMinor
                val estimatedValue = report.summary.estimatedValueMinor
                Text("Reviewed hybrid subset: ${visibleReviews.size} reviewed, " +
                    "${report.awaitingReview} unreviewed, " +
                    "${report.excludedStaleOrUnknown} excluded for absent/stale prices.",
                    modifier = Modifier.testTag("m29-exposure-counts"))
                Text("Known dated value only: ₹${BigDecimal(value, 4).toPlainString()}; " +
                    "owner-estimated share ₹${BigDecimal(estimatedValue, 4).toPlainString()}. " +
                    "Neither is an all-asset net worth.",
                    modifier = Modifier.testTag("m29-exposure-known"))
                for ((category, amount) in report.summary.totals) {
                    Text("$category • ₹${BigDecimal(amount, 4).toPlainString()}",
                        modifier = Modifier.testTag("m29-exposure-bucket-$category"))
                }
            }
        }
        if (eligible.isEmpty()) Text("No active hybrid mutual fund in this owner lens.")
        else {
            OutlinedButton(enabled = loaded && !busy,
                onClick = { menu = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("m29-exposure-picker")) {
                Text(eligible.firstOrNull { it.id == selectedId }?.name
                    ?: "Select hybrid fund to review")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                eligible.forEach { h ->
                    DropdownMenuItem(text = { Text(h.name + " • " + h.personName) },
                        onClick = {
                            selectedId = h.id
                            split = ""
                            source = ""
                            asOf = ""
                            menu = false
                        }, modifier = Modifier.testTag("m29-exposure-holding-${h.id}"))
                }
            }
            val selected = eligible.firstOrNull { it.id == selectedId }
            if (selected != null) {
                Text("Selected: ${selected.name} • ${selected.personName}. " +
                    "Enter an explicit split totalling 10000 basis points; " +
                    "example EQUITY:6500,DEBT:3500 (no sample is saved).")
                OutlinedTextField(value = split, onValueChange = { split = it.take(180) },
                    enabled = !busy, singleLine = true,
                    label = { Text("Exposure codes and basis points") },
                    modifier = Modifier.testTag("m29-exposure-split"))
                OutlinedTextField(value = source, onValueChange = { source = it.take(120) },
                    enabled = !busy, singleLine = true,
                    label = { Text("Owner's source description") },
                    modifier = Modifier.testTag("m29-exposure-source"))
                OutlinedTextField(value = asOf, onValueChange = { asOf = it.take(10) },
                    enabled = !busy, singleLine = true,
                    label = { Text("Reviewed as of YYYY-MM-DD") },
                    modifier = Modifier.testTag("m29-exposure-date"))
                OutlinedButton(enabled = !busy, onClick = { estimated = !estimated },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("m29-exposure-estimate")) {
                    Text(if (estimated) "Estimated (unverified) • tap for reviewed" else
                        "Owner-reviewed (unverified issuer) • tap for estimate")
                }
                OutlinedButton(enabled = loaded && !busy && split.isNotBlank() &&
                    source.isNotBlank() && asOf.isNotBlank(),
                    onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                val fresh = withContext(Dispatchers.IO) {
                                    books.saveHybridExposure(email, selected.id, selected.personId,
                                        asOf, source, estimated, split)
                                    books.hybridExposureReviews(email, ownerFilter)
                                }
                                reviews = fresh
                            } catch (_: Exception) {
                                error = "Review not saved. Check split, source, date and vault access."
                            } finally { busy = false }
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp).testTag("m29-exposure-save")) {
                    Text("Save owner-labelled hybrid split")
                }
                if (reviews.any { it.holdingId == selected.id }) {
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                reviews = withContext(Dispatchers.IO) {
                                    books.deleteHybridExposure(email, selected.id, selected.personId)
                                    books.hybridExposureReviews(email, ownerFilter)
                                }
                            } catch (_: Exception) {
                                error = "Review deletion rejected; reauthenticate."
                            } finally { busy = false }
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("m29-exposure-delete")) {
                        Text("Delete reviewed split (holdings unchanged)")
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("m29-exposure-error"))
    }
}
