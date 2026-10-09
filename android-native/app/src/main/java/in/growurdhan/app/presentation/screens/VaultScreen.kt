package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.growurdhan.app.presentation.VaultIntent
import `in`.growurdhan.app.presentation.VaultUiState

@Composable
fun VaultScreen(
    state: VaultUiState,
    onIntent: (VaultIntent) -> Unit,
    onAddFamilyMember: () -> Unit = {},
    onManageHoldings: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "On-device vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Financial records are stored in an encrypted SQLite database. " +
                "The database key is generated on this device and wrapped by Android Keystore.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    VaultUiState.Loading -> {
                        CircularProgressIndicator()
                        Text("Checking encrypted storage…")
                    }
                    VaultUiState.NotCreated -> {
                        Text("No native vault found", style = MaterialTheme.typography.titleLarge)
                        Text("Creating the private vault first requires confirmation of your Android screen lock.")
                        Button(onClick = { onIntent(VaultIntent.OpenOrCreate) }) {
                            Text("Create encrypted vault")
                        }
                    }
                    VaultUiState.Closed -> {
                        Text("Vault closed", style = MaterialTheme.typography.titleLarge)
                        Text("The database remains encrypted on this device. Confirm your device screen lock to reopen.")
                        Button(onClick = { onIntent(VaultIntent.OpenOrCreate) }) {
                            Text("Open encrypted vault")
                        }
                    }
                    is VaultUiState.Ready -> {
                        Text("Vault opened", style = MaterialTheme.typography.titleLarge)
                        Text("SQLCipher encryption is enabled.")
                        Text("Members: " + state.health.memberCount)
                        Button(onClick = onAddFamilyMember) {
                            Text(
                                if (state.health.memberCount == 0) "Add first family member"
                                else "Manage family members",
                            )
                        }
                        Text("Holdings: " + state.health.holdingCount)
                        Button(onClick = onManageHoldings) {
                            Text("Manage accounts & holdings")
                        }
                        Text("Ledger events: " + state.health.eventCount)
                        Text("Reference checks: " + if (state.health.foreignKeyViolations == 0) "Passed" else "Needs attention")
                        OutlinedButton(onClick = { onIntent(VaultIntent.Close) }) { Text("Close vault") }
                    }
                    is VaultUiState.Error -> {
                        Text("Vault unavailable", style = MaterialTheme.typography.titleLarge)
                        Text(state.message)
                        OutlinedButton(onClick = { onIntent(VaultIntent.Refresh) }) { Text("Try again") }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Development security boundaries", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android device confirmation and repository authorization guard native " +
                        "financial writes, but the GrowUrDhan-specific login, backup file " +
                        "pickers and full CAS/holding onboarding remain incomplete. " +
                        "The frozen parser is available only in supported ARM64 release builds. " +
                        "Do not use this development build for live financial records yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
