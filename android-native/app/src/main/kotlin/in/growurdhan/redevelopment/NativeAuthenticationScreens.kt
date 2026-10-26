package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.designsystem.GUDLockedBanner
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeRegistrationProof
import kotlinx.coroutines.launch

/** Never rememberSaveable secrets: Android saved-instance-state cannot contain passwords. */
@Composable
internal fun NativeAuthenticationScreen(
    deviceVerified: Boolean,
    unlocked: Boolean,
    deviceMessage: String,
    onDeviceChallenge: () -> Unit,
    onEnroll: suspend (NativeCredentials.Enrollment, NativeRegistrationProof.Identity, NativeRegistrationProof.Consent) -> String?,
    onLogin: suspend (String, String, NativeRegistrationProof.Consent?) -> NativeAccountRepository.SignInResult,
    onRotation: suspend (String, String, String) -> Boolean,
    onRecover: suspend (String, String, String, String, String, String, String) -> Boolean,
    onReplaceRecoveryCode: suspend (String) -> String?,
    onLock: () -> Unit,
    onOpenBooks: () -> Unit,
    onDeclineTerms: () -> Unit
) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var acceptedAt by remember { mutableStateOf<String?>(null) }
    var needsLoginConsent by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var recoveryCodeInput by remember { mutableStateOf("") }
    var oneTimeRecoveryCode by remember { mutableStateOf("") }
    var q1 by remember { mutableStateOf("school") }
    var q2 by remember { mutableStateOf("pet") }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(deviceVerified, unlocked) {
        if (!deviceVerified || unlocked) {
            password = ""
            replacement = ""
            answer1 = ""
            answer2 = ""
            recoveryCodeInput = ""
            if (!deviceVerified) oneTimeRecoveryCode = ""
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            GUDBrandLockup()
            GUDLockedBanner()
            Text(if (unlocked) "Private vault unlocked" else "Private account",
                style = MaterialTheme.typography.headlineMedium)
            Text("Your financial information remains on this device.",
                style = MaterialTheme.typography.bodyMedium)
            if (oneTimeRecoveryCode.isNotEmpty() && deviceVerified) {
                Text("SAVE THIS RECOVERY CODE — shown once, not stored in plaintext.",
                    style = MaterialTheme.typography.titleMedium)
                Text(oneTimeRecoveryCode, style = MaterialTheme.typography.bodyLarge)
                Text("Store offline. Recovery requires this code, both answers and device authentication.")
                Button(onClick = {
                    oneTimeRecoveryCode = ""
                    onLock()
                }) { Text("I have saved the recovery code") }
            } else if (unlocked) {
                Text("Account verified. Financial books remain on this device.")
                Button(onClick = onOpenBooks, enabled = !busy) { Text("Open private family books") }
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            oneTimeRecoveryCode = onReplaceRecoveryCode(email).orEmpty()
                            message = if (oneTimeRecoveryCode.isEmpty())
                                "Code replacement denied" else ""
                        } finally { busy = false }
                    }
                }) { Text("Replace saved recovery code") }
                Button(onClick = { oneTimeRecoveryCode = ""; onLock() }) { Text("Lock now") }
            } else {
                if (!deviceVerified) {
                    Text("Confirm your Android screen lock before creating or opening a vault.")
                    Button(onClick = onDeviceChallenge, enabled = !busy) {
                        Text("Verify this device")
                    }
                } else {
                    Text("Device verified. Enter your account credentials.")
                }
                if (deviceMessage.isNotEmpty()) Text(deviceMessage,
                    color = MaterialTheme.colorScheme.error)
                OutlinedTextField(email, { email = it }, label = { Text("Email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                if (mode == "register") {
                    OutlinedTextField(first, { first = it }, label = { Text("First name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedTextField(last, { last = it }, label = { Text("Last name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedTextField(pan, { pan = it }, label = { Text("PAN (stored encrypted)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth (YYYY-MM-DD)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode != "recovery") {
                    OutlinedTextField(password, { password = it },
                        label = { Text(if (mode == "rotation") "Current password" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode == "recovery") {
                    OutlinedTextField(recoveryCodeInput, { recoveryCodeInput = it },
                        label = { Text("Offline recovery code") },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode == "rotation" || mode == "recovery") {
                    OutlinedTextField(replacement, { replacement = it },
                        label = { Text("New password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode == "register" || mode == "recovery") {
                    Text("Select your original recovery questions and enter both private answers.")
                    QuestionPicker("First recovery question", q1, !busy) { q1 = it }
                    OutlinedTextField(answer1, { answer1 = it },
                        label = { Text("First answer") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    QuestionPicker("Second recovery question", q2, !busy) { q2 = it }
                    OutlinedTextField(answer2, { answer2 = it },
                        label = { Text("Second answer") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode == "register" || (mode == "login" && needsLoginConsent)) {
                    NativeTermsGate(acceptedAt, !busy,
                        onAccept = { acceptedAt = it },
                        onDecline = {
                            acceptedAt = null
                            onDeclineTerms()
                        })
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (busy) CircularProgressIndicator()
                Button(enabled = deviceVerified && !busy, onClick = {
                    val emailValue = email
                    val pwValue = password
                    val newValue = replacement
                    val codeValue = recoveryCodeInput
                    val firstQuestion = q1
                    val secondQuestion = q2
                    val firstAnswer = answer1
                    val secondAnswer = answer2
                    val input = NativeCredentials.Enrollment(email, first, last,
                        pwValue, q1, answer1, q2, answer2)
                    busy = true
                    message = ""
                    scope.launch {
                        try {
                            when (mode) {
                                "register" -> {
                                    val consentTime = acceptedAt
                                    val code = if (consentTime == null) null else onEnroll(
                                        input,
                                        NativeRegistrationProof.Identity(pan, dob),
                                        NativeRegistrationProof.Consent(
                                            NativeFrozenTerms.VERSION, NativeFrozenTerms.TEXT_ID,
                                            consentTime, scrolledToEnd = true))
                                    message = if (code != null) "Account created. Save the recovery code before leaving."
                                        else "Registration denied or current Terms not accepted. If a vault already exists, sign in and replace the offline recovery code. Existing books are preserved."
                                    if (code != null) {
                                        pan = ""; dob = ""; acceptedAt = null
                                        oneTimeRecoveryCode = code
                                        mode = "login"
                                    }
                                }
                                "recovery" -> {
                                    val success = onRecover(emailValue, codeValue,
                                        firstQuestion, firstAnswer, secondQuestion, secondAnswer, newValue)
                                    message = if (success) "Password recovered. Verify device and sign in."
                                        else "Recovery rejected. Vault data was preserved."
                                    if (success) mode = "login"
                                }
                                "rotation" -> {
                                    val success = onRotation(emailValue, pwValue, newValue)
                                    message = if (success) "Password changed. Verify device to sign in."
                                        else "Password change denied. The existing vault was preserved."
                                    if (success) mode = "login"
                                }
                                else -> {
                                    val consent = if (needsLoginConsent && acceptedAt != null) {
                                        NativeRegistrationProof.Consent(
                                            NativeFrozenTerms.VERSION, NativeFrozenTerms.TEXT_ID,
                                            acceptedAt!!, scrolledToEnd = true)
                                    } else null
                                    message = when (onLogin(emailValue, pwValue, consent)) {
                                        NativeAccountRepository.SignInResult.UNLOCKED -> {
                                            needsLoginConsent = false
                                            acceptedAt = null
                                            ""
                                        }
                                        NativeAccountRepository.SignInResult.TERMS_REQUIRED -> {
                                            needsLoginConsent = true
                                            acceptedAt = null
                                            "Read the full current Terms to continue. Your existing vault is preserved."
                                        }
                                        NativeAccountRepository.SignInResult.ROTATION_REQUIRED ->
                                            "Password expired. Change it using your current password."
                                        NativeAccountRepository.SignInResult.DENIED ->
                                            "Unable to unlock. Check your credentials."
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            message = "Authentication could not be completed."
                        } finally {
                            password = ""
                            replacement = ""
                            recoveryCodeInput = ""
                            answer1 = ""
                            answer2 = ""
                            busy = false
                        }
                    }
                }) {
                    Text(when (mode) {
                        "register" -> "Create private vault"
                        "rotation" -> "Change password"
                        "recovery" -> "Recover password"
                        else -> "Unlock private vault"
                    })
                }
                TextButton(enabled = !busy, onClick = {
                    mode = if (mode == "register") "login" else "register"
                    password = ""; replacement = ""; answer1 = ""; answer2 = ""; acceptedAt = null; needsLoginConsent = false; message = ""
                }) {
                    Text(if (mode == "register") "Already registered? Sign in"
                         else "New account? Register")
                }
                TextButton(enabled = !busy, onClick = {
                    mode = if (mode == "rotation") "login" else "rotation"
                    password = ""; replacement = ""; message = ""
                }) {
                    Text(if (mode == "rotation") "Back to sign in" else "Change password")
                }
                TextButton(enabled = !busy, onClick = {
                    mode = if (mode == "recovery") "login" else "recovery"
                    password = ""; replacement = ""; recoveryCodeInput = ""
                    answer1 = ""; answer2 = ""; message = ""
                }) {
                    Text(if (mode == "recovery") "Back to sign in" else "Forgot password? Use offline recovery code")
                }
                Text("Accounts without a provisioned offline recovery code cannot be reset with answers alone.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val knownQuestions = listOf(
    "maiden" to "Mother's maiden name",
    "school" to "First school",
    "pet" to "First pet",
    "city" to "City of birth",
    "teacher" to "Favourite teacher",
    "vehicle" to "First car or bike"
)
@Composable
private fun QuestionPicker(label: String, selected: String,
    enabled: Boolean, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium)
    OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
        Text(knownQuestions.firstOrNull { it.first == selected }?.second ?: "Select")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        for ((id, title) in knownQuestions) {
            DropdownMenuItem(text = { Text(title) }, onClick = {
                choose(id)
                expanded = false
            })
        }
    }
}