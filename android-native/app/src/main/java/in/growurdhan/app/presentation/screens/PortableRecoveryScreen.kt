package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.growurdhan.app.presentation.PortableRecoveryMode
import `in`.growurdhan.app.presentation.PortableRecoveryUiState

sealed interface PortableRecoveryIntent {
    data object SelectExportDestination : PortableRecoveryIntent
    data object SelectRestoreSource : PortableRecoveryIntent
    data class ConfirmPassphrase(val password: String, val repeated: String) : PortableRecoveryIntent
    data object Cancel : PortableRecoveryIntent
}

/**
 * Passwords are transient Compose state, never stored in the vault or
 * SavedStateHandle. The final prompt is Android's own device credential.
 */
@Composable
fun PortableRecoveryScreen(
    state: PortableRecoveryUiState,
    onIntent: (PortableRecoveryIntent) -> Unit,
    onReturnToVault: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Backup & recovery", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Portable backups use a separate password and can be recovered on a new phone. " +
                "That password does not decrypt your local SQLCipher database.",
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Existing data is never overwritten", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Restore works only into a brand-new or completely empty encrypted vault. " +
                        "A lost backup password cannot be recovered by GrowUrDhan.",
                )
                Text("Use a private, trusted document location. Never share the encrypted backup or password.")
            }
        }
        when (state) {
            PortableRecoveryUiState.Empty -> {
                Button(
                    onClick = { onIntent(PortableRecoveryIntent.SelectExportDestination) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export encrypted backup") }
                OutlinedButton(
                    onClick = { onIntent(PortableRecoveryIntent.SelectRestoreSource) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore encrypted backup") }
            }
            is PortableRecoveryUiState.EnterPassphrase -> {
                key(state.mode) {
                    var password by remember { mutableStateOf("") }
                    var repeated by remember { mutableStateOf("") }
                    val exporting = state.mode == PortableRecoveryMode.EXPORT
                    Text(
                        if (exporting) "Set the portable backup password"
                        else "Enter the password used to make this backup",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (it.length <= 1024) password = it },
                        label = { Text("Backup password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (exporting) {
                        OutlinedTextField(
                            value = repeated,
                            onValueChange = { if (it.length <= 1024) repeated = it },
                            label = { Text("Confirm backup password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        enabled = password.length >= 12 && (!exporting || password == repeated),
                        onClick = {
                            onIntent(PortableRecoveryIntent.ConfirmPassphrase(password, repeated))
                            password = ""
                            repeated = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirm device lock and ${if (exporting) "export" else "restore"}")
                    }
                }
                OutlinedButton(
                    onClick = { onIntent(PortableRecoveryIntent.Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel") }
            }
            PortableRecoveryUiState.Working -> {
                CircularProgressIndicator()
                Text("Processing encrypted backup on this device…")
            }
            is PortableRecoveryUiState.Completed -> {
                Text(
                    if (state.mode == PortableRecoveryMode.EXPORT)
                        "Encrypted backup saved" else "Encrypted financial book recovered",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Keep the backup password separate from the backup file.")
                Button(onClick = onReturnToVault) { Text("Return to Vault") }
            }
            is PortableRecoveryUiState.Error -> {
                Text("Backup unavailable", style = MaterialTheme.typography.titleMedium)
                Text(state.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { onIntent(PortableRecoveryIntent.Cancel) }) {
                    Text("Dismiss")
                }
            }
        }
    }
}
