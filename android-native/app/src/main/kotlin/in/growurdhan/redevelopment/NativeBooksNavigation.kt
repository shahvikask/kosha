package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val nativeMainDestinations = listOf("Home", "Holdings", "Statements", "Insights", "Family")

internal fun NativeBooksMainDestination(route: String) = when (route) {
    "Documents", "Records", "Statements" -> "Statements"
    "Goals", "Insights" -> "Insights"
    "Accounts", "Settings", "Family" -> "Family"
    "Holdings" -> "Holdings"
    else -> "Home"
}

/** Five persistent bottom destinations; labeled, touch-sized and screen-reader selected. */
@Composable
internal fun NativeBooksNavigation(activeTab: String, onNavigate: (String) -> Unit) {
    val active = NativeBooksMainDestination(activeTab)
    NavigationBar(modifier = Modifier.fillMaxWidth().testTag("books-nav")) {
        nativeMainDestinations.forEach { name ->
            val isSelected = active == name
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(name) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("books-tab-$name")
                    .semantics {
                        selected = isSelected
                        contentDescription = "$name tab"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                icon = { Text(name.take(1), maxLines = 1) },
                label = { Text(name, maxLines = 2) },
                alwaysShowLabel = true
            )
        }
    }
}

/** Existing specialized routes remain available inside the five destinations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeBooksSubNavigation(route: String, onNavigate: (String) -> Unit) {
    val choices = when (NativeBooksMainDestination(route)) {
        "Family" -> listOf("Family", "Accounts", "Settings")
        "Statements" -> listOf("Documents", "Records")
        "Insights" -> listOf("Goals")
        else -> emptyList()
    }
    if (choices.size < 2) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("books-subnav"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { name ->
            val isSelected = route == name
            OutlinedButton(onClick = { onNavigate(name) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("books-subtab-$name")
                    .semantics {
                        selected = isSelected
                        contentDescription = "$name section"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }) {
                Text(name)
            }
        }
    }
}
