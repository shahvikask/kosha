package \`in\`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P36 explicit legal-review boundary. No opt-in happens on render, open or login.
 * Scroll-to-end and a distinct checkbox are both required for the visible button.
 */
@Composable
internal fun NativePrivacyConsentCard(
    email: String, repo: NativePrivacyConsentRepository
) {
    var accepted by remember(email) { mutableStateOf(false) }
    var busy by remember(email) { mutableStateOf(false) }
    var error by remember(email) { mutableStateOf("") }
    var acknowledged by remember(email) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(email) {
        accepted = runCatching { withContext(Dispatchers.IO) {
            repo.accepted(email) != null
        } }.getOrDefault(false)
    }
    val end = scroll.maxValue > 0 && scroll.value >= scroll.maxValue
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Data handling & Privacy acceptance",
            style = MaterialTheme.typography.titleLarge)
        Text("Review the current on-device notice separately from Terms. " +
            "Legal review of the final Privacy Policy is still required before release.")
        Column(Modifier.fillMaxWidth().height(220.dp)
            .testTag("p36-privacy-scroller").verticalScroll(scroll).padding(8.dp)) {
            Text(NativePrivacyNotice.TEXT, style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (end) "End of data notice reached"
            else "Scroll to the end of the notice to continue.")
        if (accepted) {
            Text("Versioned data notice accepted and stored inside your encrypted vault.",
                modifier = Modifier.testTag("p36-privacy-accepted"))
        } else {
            androidx.compose.foundation.layout.Row {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = { acknowledged = it },
                    enabled = !busy,
                    modifier = Modifier.testTag("p36-privacy-checkbox")
                )
                Text("I have read and acknowledge this data-handling notice.")
            }
            Button(enabled = end && acknowledged && !busy,
                modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("p36-privacy-accept"),
                onClick = {
                    if (!end || !acknowledged || busy) return@Button
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            val proof = NativePrivacyConsentRepository.Review(
                                NativePrivacyNotice.TEXT_ID, reachedEnd = true,
                                explicitlyAcknowledged = true,
                                acceptedAtMillis = System.currentTimeMillis())
                            withContext(Dispatchers.IO) { repo.accept(email, proof) }
                            accepted = true
                        } catch (_: Exception) {
                            error = "Unable to record acceptance in the unlocked encrypted vault."
                        } finally { busy = false }
                    }
                }) { Text("Accept current data notice") }
        }
        if (error.isNotEmpty()) Text(error)
    }
}
