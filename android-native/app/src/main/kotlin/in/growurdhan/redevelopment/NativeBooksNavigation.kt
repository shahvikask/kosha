package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Wrapping native destination controls: fixed three-button rows overflow at
 * 360dp / 200% font scale. Selected destination is also announced by TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeBooksNavigation(activeTab: String, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        "Home" to "Home",
        "Holdings" to "Holdings",
        "Family" to "Family",
        "Accounts" to "Accounts",
        "Goals" to "Goals",
        "Records" to "Records",
        "Documents" to "Documents and imports",
        "Settings" to "Settings and privacy"
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("books-nav"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        destinations.forEach { (key, label) ->
            OutlinedButton(
                onClick = { onNavigate(key) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("books-tab-$key")
                    .semantics { selected = activeTab == key }
            ) {
                Text(label)
            }
        }
    }
}
