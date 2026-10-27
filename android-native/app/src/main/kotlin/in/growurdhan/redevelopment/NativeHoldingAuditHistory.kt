package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * P15 bounded, virtualized, append-only audit presentation.
 * Parent enforces owner lens and loads history only after explicit selection.
 * Stable immutable event IDs are keys; no synthetic aggregates or event edits.
 * Height constraint makes same-direction nested scrolling safe inside Books.
 */
@Composable
internal fun NativeHoldingAuditHistory(
    events: List<NativeBooksRepository.EventRow>
) {
    Text("Append-only audit events: ${events.size}",
        style = MaterialTheme.typography.titleMedium)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(300.dp).testTag("m27-history-list")
    ) {
        items(items = events, key = { it.id }) { event ->
            Column {
                Text(
                    "${event.date} • ${event.kind} • " +
                        "${NativeBooksRepository.formatMinor(event.quantityScaled, 8)} units • " +
                        "amount ₹${NativeBooksRepository.formatMinor(event.amountMinor, 4)} • " +
                        "charges ₹${NativeBooksRepository.formatMinor(event.chargesMinor, 4)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Event ID ${event.id}" +
                        (event.supersedes?.let { " • corrects full event ID $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("m27-history-${event.id}")
                )
                HorizontalDivider()
            }
        }
    }
}
