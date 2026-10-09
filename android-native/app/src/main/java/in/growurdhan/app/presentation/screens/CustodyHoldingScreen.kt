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
import `in`.growurdhan.app.presentation.CustodyHoldingIntent
import `in`.growurdhan.app.presentation.CustodyHoldingUiState
import `in`.growurdhan.domain.vault.CustodyHoldingOnboarding

/** Protected CAS enrolment: only registrar names, not PAN or passwords. */
@Composable
fun CustodyHoldingScreen(
    state: CustodyHoldingUiState,
    onIntent: (CustodyHoldingIntent) -> Unit,
    onBackToVault: () -> Unit,
    onContinueToStatements: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Accounts & holdings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Add the registrar account and exact ISIN/folio for a family member. " +
                "A new holding starts with zero units and unknown purchase cost. " +
                "Only a separately reviewed CAS can record opening units.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            CustodyHoldingUiState.Locked -> {
                Text("Unlock the encrypted vault before enrolling accounts or holdings.")
                OutlinedButton(onClick = onBackToVault) { Text("Go to Vault") }
            }
            CustodyHoldingUiState.Loading, CustodyHoldingUiState.Saving -> {
                CircularProgressIndicator()
                Text(
                    if (state == CustodyHoldingUiState.Loading) "Reading encrypted accounts…"
                    else "Saving encrypted registration…",
                )
            }
            is CustodyHoldingUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onIntent(CustodyHoldingIntent.Refresh) }) { Text("Try again") }
                OutlinedButton(onClick = onBackToVault) { Text("Go to Vault") }
            }
            is CustodyHoldingUiState.AwaitingCredential -> {
                Text("Confirm your device screen lock before recording this financial registration.")
                Text("Nothing has been written yet. Cancelling leaves the book unchanged.")
                OutlinedButton(onClick = { onIntent(CustodyHoldingIntent.Cancel) }) {
                    Text("Cancel registration")
                }
            }
            is CustodyHoldingUiState.Saved -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Registration saved", style = MaterialTheme.typography.titleLarge)
                        Text(state.message)
                        Text("No purchase transaction or historical cost was invented.")
                    }
                }
                Button(
                    onClick = { onIntent(CustodyHoldingIntent.Refresh) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add an account or holding") }
                Button(
                    onClick = onContinueToStatements,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue to Statements") }
            }
            is CustodyHoldingUiState.Ready -> {
                val book = state.book
                if (book.members.isEmpty()) {
                    Text("Add a family member before creating registrar accounts.")
                    OutlinedButton(onClick = onBackToVault) { Text("Go to Vault") }
                } else {
                    var selectedOwner by remember(book.members) {
                        mutableStateOf(book.members.first().id)
                    }
                    Text("Family owner", style = MaterialTheme.typography.titleMedium)
                    book.members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedOwner == member.id,
                                onClick = { selectedOwner = member.id },
                            )
                            Text(member.displayName)
                        }
                    }

                    val accounts = book.accounts.filter { it.ownerId == selectedOwner }
                    var registrar by remember(selectedOwner) { mutableStateOf("CAMS") }
                    var label by remember(selectedOwner) { mutableStateOf("") }
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("1. Add registrar account", style = MaterialTheme.typography.titleMedium)
                            Text("Use a nickname; do not enter your PAN, password, or full account number.")
                            CustodyHoldingOnboarding.registrarTypes.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = registrar == option,
                                        onClick = { registrar = option },
                                    )
                                    Text(option)
                                }
                            }
                            OutlinedTextField(
                                value = label,
                                onValueChange = { if (it.length <= 64) label = it },
                                label = { Text("Account nickname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentDescription = "Registrar account nickname input"
                                },
                            )
                            Button(
                                enabled = label.trim().isNotEmpty(),
                                onClick = {
                                    onIntent(CustodyHoldingIntent.StageAccount(
                                        selectedOwner, registrar, label,
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save registrar account securely") }
                        }
                    }

                    var selectedAccount by remember(selectedOwner, accounts) {
                        mutableStateOf(accounts.firstOrNull()?.id)
                    }
                    var fundName by remember(selectedOwner, accounts) { mutableStateOf("") }
                    var isin by remember(selectedOwner, accounts) { mutableStateOf("") }
                    var folio by remember(selectedOwner, accounts) { mutableStateOf("") }
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("2. Enrol a mutual fund", style = MaterialTheme.typography.titleMedium)
                            if (accounts.isEmpty()) {
                                Text("Save a CAMS or KFINTECH registrar account first.")
                            } else {
                                Text("Choose its registrar account")
                                accounts.forEach { account ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = selectedAccount == account.id,
                                            onClick = { selectedAccount = account.id },
                                        )
                                        Text("${account.accountType} · ${account.identifierMasked}")
                                    }
                                }
                                OutlinedTextField(
                                    value = fundName,
                                    onValueChange = { if (it.length <= 200) fundName = it },
                                    label = { Text("Mutual fund scheme name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = isin,
                                    onValueChange = { if (it.length <= 12) isin = it.uppercase() },
                                    label = { Text("ISIN (12 characters)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().semantics {
                                        contentDescription = "CAS ISIN input"
                                    },
                                )
                                OutlinedTextField(
                                    value = folio,
                                    onValueChange = { if (it.length <= 64) folio = it },
                                    label = { Text("CAS folio") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().semantics {
                                        contentDescription = "CAS folio input"
                                    },
                                )
                                Text(
                                    "The ISIN, folio and name must agree with the parsed CAS. " +
                                        "An ambiguous or unknown identity is never auto-matched.",
                                )
                                Button(
                                    enabled = selectedAccount != null && fundName.trim().isNotEmpty() &&
                                        isin.length == 12 && folio.trim().isNotEmpty(),
                                    onClick = {
                                        selectedAccount?.let { id ->
                                            onIntent(CustodyHoldingIntent.StageHolding(
                                                selectedOwner, id, fundName, isin, folio,
                                            ))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Enrol mutual fund securely") }
                            }
                        }
                    }

                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Enrolled holdings", style = MaterialTheme.typography.titleMedium)
                    val enrolled = book.casIdentities.filter { it.ownerId == selectedOwner }
                    if (enrolled.isEmpty()) {
                        Text("No CAS holdings enrolled for this family member.")
                    } else {
                        enrolled.forEach { mapping ->
                            val record = book.holdings.firstOrNull { it.id == mapping.holdingId }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(record?.name ?: "Holding", style = MaterialTheme.typography.titleSmall)
                                    Text("ISIN: ${mapping.isin}")
                                    Text("Folio: ••••${mapping.folio.takeLast(4)}")
                                    Text("Registrar: ${mapping.accountType}")
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onContinueToStatements,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue to Statements") }
                    OutlinedButton(onClick = onBackToVault) { Text("Go to Vault") }
                }
            }
        }
    }
}
