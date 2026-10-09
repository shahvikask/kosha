package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.growurdhan.app.presentation.StatementReviewIntent
import `in`.growurdhan.app.presentation.StatementReviewUiState
import `in`.growurdhan.core.model.SnapshotResult
import `in`.growurdhan.domain.finance.ReconciliationOutcome
import java.math.BigDecimal

/**
 * Financial consent is deliberately explicit. The confirmation checkbox is not
 * saved across navigation/process restart. No parser-produced statement = no
 * import button. Device credential is checked again by MainActivity.
 */
@Composable
fun StatementReviewScreen(
    state: StatementReviewUiState,
    onIntent: (StatementReviewIntent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Review a statement",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Only approved changes will be recorded in your encrypted financial book.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            StatementReviewUiState.Empty -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No statement ready for review", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose a PDF on your device and explicitly select its family owner. " +
                                "Only verified on-device CAS output matching an existing holding can proceed.",
                        )
                        Text(
                            "The frozen CASPARSER/PDFium engine is packaged only with the ARM64 release variant; " +
                                "unsupported builds refuse the import.",
                        )
                        Button(
                            onClick = { onIntent(StatementReviewIntent.LoadOwners) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Select CAS PDF") }
                    }
                }
            }
            is StatementReviewUiState.SelectOwner -> {
                var selected by remember(state.members) {
                    mutableStateOf<`in`.growurdhan.core.model.OwnerId?>(null)
                }
                Text(
                    "Which family member owns this CAS? Names and identities must match the PDF. " +
                        "Ambiguous ISIN/folio mappings will be blocked.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == member.id,
                            onClick = { selected = member.id },
                        )
                        Text(member.displayName)
                    }
                }
                Button(
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selected?.let { onIntent(StatementReviewIntent.PickPdf(it)) }
                    },
                ) { Text("Choose PDF from device") }
                OutlinedButton(
                    onClick = { onIntent(StatementReviewIntent.Cancel) },
                ) { Text("Cancel") }
            }
            StatementReviewUiState.Working -> {
                CircularProgressIndicator()
                Text("Checking the encrypted book…")
            }
            is StatementReviewUiState.Error -> {
                Text("Review unavailable", style = MaterialTheme.typography.titleMedium)
                Text(state.message)
                OutlinedButton(onClick = { onIntent(StatementReviewIntent.Cancel) }) {
                    Text("Dismiss")
                }
            }
            is StatementReviewUiState.Completed -> {
                Text(
                    if (state.result == SnapshotResult.IMPORTED)
                        "Statement recorded" else "Statement already recorded",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("The encrypted ledger was updated without recording a duplicate purchase.")
                OutlinedButton(onClick = { onIntent(StatementReviewIntent.Cancel) }) { Text("Done") }
            }
            is StatementReviewUiState.Reviewing -> {
                val receipt = state.receipt
                val plan = receipt.plan
                val conflicts = plan.lines.count { it.outcome == ReconciliationOutcome.CONFLICT }
                val events = plan.lines.sumOf { it.proposedEvents.size }
                var acknowledged by remember(receipt.reviewSeal) { mutableStateOf(false) }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Statement date: ${plan.statementDate}")
                        Text("Source fingerprint ending: …${plan.sourceSha256.takeLast(8)}")
                        Text("Positions reviewed: ${plan.lines.size}")
                        Text("Proposed ledger adjustments: $events")
                        Text("Estimated change in value: ₹${plan.estimatedValueChange.rupees().toPlainString()}")
                        Text("Purchase cost can remain unknown when the registrar reports only market value.")
                    }
                }

                plan.lines.forEachIndexed { index, item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Statement position ${index + 1} — ${item.outcome.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Units on statement: ${BigDecimal.valueOf(item.source.quantityScaled, 8).toPlainString()}")
                            Text("Units previously recorded: ${BigDecimal.valueOf(item.expectedQtyScaled, 8).toPlainString()}")
                            Text("Proposed unit difference: ${BigDecimal.valueOf(item.deltaQtyScaled, 8).toPlainString()}")
                            Text(item.explanation)
                            if (item.outcome == ReconciliationOutcome.CONFLICT) {
                                Text(
                                    "Blocked: cannot approve this statement until the conflict is resolved.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                plan.warnings.forEach { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                state.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (conflicts == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                            modifier = Modifier.semantics {
                                contentDescription = "Confirm statement changes"
                            },
                        )
                        Text(
                            "I have checked the statement date, quantities and proposed changes.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(
                    enabled = acknowledged && conflicts == 0,
                    onClick = { onIntent(StatementReviewIntent.RequestApproval) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (conflicts > 0) "Approval blocked by conflicts" else "Confirm and authenticate approval")
                }
                OutlinedButton(
                    onClick = { onIntent(StatementReviewIntent.Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel review") }
            }
        }
    }
}
