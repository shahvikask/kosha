package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

/** Exact 21 frozen clauses with a genuine nested scroll-to-end acceptance boundary. */
@Composable
internal fun NativeTermsGate(acceptedAt: String?, enabled: Boolean,
    onAccept: (String) -> Unit, onDecline: () -> Unit) {
    val state = rememberScrollState()
    val atEnd = state.maxValue > 0 && state.value >= state.maxValue
    var acknowledged by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Current Terms and Conditions • ${NativeFrozenTerms.VERSION}",
            style = MaterialTheme.typography.titleMedium)
        Text("Read all sections to the end before accepting. Declining prevents registration.")
        Column(Modifier.fillMaxWidth().height(280.dp).verticalScroll(state)
            .padding(8.dp)) {
            Text(NativeFrozenTerms.fullText, style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (atEnd) "End of current version reached"
            else "Scroll inside the Terms panel to continue.",
            style = MaterialTheme.typography.bodySmall)
        if (acceptedAt != null) {
            Text("Terms accepted for this registration. Evidence will be stored inside SQLCipher.")
        } else {
            Button(enabled = enabled && atEnd && !acknowledged, onClick = {
                if (state.value >= state.maxValue && state.maxValue > 0) {
                    acknowledged = true
                    onAccept(Instant.now().toString())
                }
            }) { Text("I accept the full versioned Terms") }
            Button(enabled = enabled, onClick = onDecline) { Text("Do not accept") }
        }
    }
}
