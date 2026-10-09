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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import `in`.growurdhan.app.presentation.FamilyMemberIntent
import `in`.growurdhan.app.presentation.FamilyMemberUiState
import `in`.growurdhan.domain.vault.FamilyMemberOnboarding

/** Private on-device family setup; no names persist in saved UI state. */
@Composable
fun FamilyMemberScreen(
    state: FamilyMemberUiState,
    onIntent: (FamilyMemberIntent) -> Unit,
    onContinueToStatements: () -> Unit,
    onReturnToVault: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Family members",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Set up the owner of each financial account before importing a CAS statement. " +
                "Names and relationships stay in your encrypted on-device vault.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            FamilyMemberUiState.Locked -> {
                Text("Unlock the encrypted vault before adding family members.")
                OutlinedButton(onClick = onReturnToVault) { Text("Go to Vault") }
            }
            FamilyMemberUiState.Loading, FamilyMemberUiState.Saving -> {
                CircularProgressIndicator()
                Text(
                    if (state == FamilyMemberUiState.Loading) "Reading the family vault…"
                    else "Saving to encrypted storage…",
                )
            }
            is FamilyMemberUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { onIntent(FamilyMemberIntent.Refresh) }) {
                    Text("Try again")
                }
                OutlinedButton(onClick = onReturnToVault) { Text("Go to Vault") }
            }
            is FamilyMemberUiState.AwaitingCredential -> {
                Text("Confirm your device screen lock before adding this family member.")
                Text("Nothing is written until that confirmation succeeds.")
                OutlinedButton(onClick = { onIntent(FamilyMemberIntent.Cancel) }) {
                    Text("Cancel addition")
                }
            }
            is FamilyMemberUiState.Saved -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Family member saved", style = MaterialTheme.typography.titleLarge)
                        Text(state.member.displayName)
                        Text(
                            if (state.member.isPrimary) "Primary owner (Self)"
                            else state.member.relationship,
                        )
                        Text(
                            "The member is now available for statement ownership. " +
                                "CAS funds still require existing verified holding mappings.",
                        )
                    }
                }
                Button(
                    onClick = onContinueToStatements,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue to Statements") }
                OutlinedButton(onClick = { onIntent(FamilyMemberIntent.Refresh) }) {
                    Text("Add another family member")
                }
                OutlinedButton(onClick = onReturnToVault) { Text("Go to Vault") }
            }
            is FamilyMemberUiState.Ready -> {
                val isFirst = state.members.isEmpty()
                var name by remember(state.members) { mutableStateOf("") }
                var relationship by remember(state.members) {
                    mutableStateOf(if (isFirst) "Self" else "Spouse")
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (isFirst) "Add yourself as the primary owner"
                            else "Add another family member",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 100) name = it },
                            label = { Text("Family member name") },
                            supportingText = { Text("Use a distinct display name, not an account number or PAN.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Family member name input"
                            },
                        )
                        if (isFirst) {
                            Text("Relationship: Self (first family member)")
                        } else {
                            Text("Relationship", style = MaterialTheme.typography.titleSmall)
                            FamilyMemberOnboarding.additionalRelationships.forEach { option ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    RadioButton(
                                        selected = relationship == option,
                                        onClick = { relationship = option },
                                    )
                                    Text(option)
                                }
                            }
                        }
                        state.validationMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            enabled = name.trim().isNotBlank(),
                            onClick = {
                                onIntent(FamilyMemberIntent.Stage(
                                    displayName = name,
                                    relationship = if (isFirst) "Self" else relationship,
                                ))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue and authenticate")
                        }
                    }
                }
                if (state.members.isNotEmpty()) {
                    Text(
                        "Existing family members (${state.members.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.members.forEach { member ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(if (member.isPrimary) "Primary owner" else member.relationship)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onReturnToVault) { Text("Go to Vault") }
            }
        }
    }
}
