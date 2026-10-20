package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.model.NativeRegistrationProof
import kotlinx.coroutines.launch

/** Restore secrets live only in ephemeral Compose state, never savedInstanceState. */
@Composable
internal fun NativeRestoreScreen(
    deviceVerified: Boolean,
    staged: Boolean,
    previewAction: suspend (RestoreIdentityGate.Credentials) -> NativeRestoreFlow.Preview?,
    publishAction: suspend (RestoreIdentityGate.Credentials, NativeRestoreFlow.Preview, NativeRegistrationProof.Consent) -> Boolean,
    selectEncryptedFile: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var firstId by remember { mutableStateOf("school") }
    var firstAnswer by remember { mutableStateOf("") }
    var secondId by remember { mutableStateOf("pet") }
    var secondAnswer by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var termsAcceptedAt by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<NativeRestoreFlow.Preview?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun proof() = RestoreIdentityGate.Credentials(
        email, password, pan, dob, firstId, firstAnswer, secondId, secondAnswer)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Restore encrypted legacy backup — new empty device only")
        Text("No data is sent to a server. The backup and identity evidence remain on this phone.")
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("Cancel restore") }
        Button(onClick = selectEncryptedFile, enabled = deviceVerified && !busy && !staged) {
            Text(if (staged) "Encrypted archive staged" else "Choose encrypted .kosha backup")
        }
        if (staged && deviceVerified) {
            OutlinedTextField(email, { email = it; preview = null; confirmed = false }, label = { Text("Original account email") })
            OutlinedTextField(password, { password = it; preview = null; confirmed = false }, label = { Text("Original account password") },
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pan, { pan = it; preview = null; confirmed = false }, label = { Text("Original full PAN") })
            OutlinedTextField(dob, { dob = it; preview = null; confirmed = false }, label = { Text("Original date of birth") })
            Text("Use the original question identifiers (e.g. school, pet).")
            OutlinedTextField(firstId, { firstId = it; preview = null; confirmed = false }, label = { Text("First question ID") })
            OutlinedTextField(firstAnswer, { firstAnswer = it; preview = null; confirmed = false },
                label = { Text("First answer") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(secondId, { secondId = it; preview = null; confirmed = false }, label = { Text("Second question ID") })
            OutlinedTextField(secondAnswer, { secondAnswer = it; preview = null; confirmed = false },
                label = { Text("Second answer") }, visualTransformation = PasswordVisualTransformation())
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        confirmed = false
                        preview = previewAction(proof())
                        message = if (preview == null) "Identity proof or archive rejected" else ""
                    } finally { busy = false }
                }
            }) { Text("Verify identity and preview") }
            preview?.let { p ->
                NativeTermsGate(termsAcceptedAt, deviceVerified && !busy,
                    onAccept = { termsAcceptedAt = it }, onDecline = onBack)
                Text("Verified archive: family members ${p.members}, holdings ${p.holdings}, money events ${p.ledger}.")
                Text("Only restore to a completely empty vault. This cannot merge with existing data.")
                androidx.compose.material3.Checkbox(checked = confirmed,
                    onCheckedChange = { confirmed = it })
                Text("I approve creating a new encrypted vault from this exact verified archive.")
                Button(enabled = confirmed && termsAcceptedAt != null && !busy && deviceVerified, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val acceptedAt = requireNotNull(termsAcceptedAt)
                            val consent = NativeRegistrationProof.Consent(
                                NativeFrozenTerms.VERSION, NativeFrozenTerms.TEXT_ID,
                                acceptedAt, scrolledToEnd = true)
                            message = if (publishAction(proof(), p, consent))
                                "Restore completed. Close and reopen the app to authenticate."
                            else "Restore denied. Existing state was not replaced."
                            preview = null
                            confirmed = false
                        } finally { busy = false }
                    }
                }) { Text("Confirm one-time restore") }
            }
        }
        if (message.isNotEmpty()) Text(message)
    }
}
