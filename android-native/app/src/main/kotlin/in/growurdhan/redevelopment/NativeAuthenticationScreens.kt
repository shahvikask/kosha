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
import kotlinx.coroutines.launch

/** Never rememberSaveable secrets: Android saved-instance-state cannot contain passwords. */
@Composable
internal fun NativeAuthenticationScreen(
    deviceVerified: Boolean,
    unlocked: Boolean,
    deviceMessage: String,
    onDeviceChallenge: () -> Unit,
    onEnroll: suspend (NativeCredentials.Enrollment) -> Boolean,
    onLogin: suspend (String, String) -> NativeAccountRepository.SignInResult,
    onRotation: suspend (String, String, String) -> Boolean,
    onLock: () -> Unit
) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
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
            if (unlocked) {
                Text("Authentication completed. Portfolio screens are not enabled in this M25 increment.")
                Button(onClick = onLock) { Text("Lock now") }
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
                }
                OutlinedTextField(password, { password = it },
                    label = { Text(if (mode == "rotation") "Current password" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                if (mode == "rotation") {
                    OutlinedTextField(replacement, { replacement = it },
                        label = { Text("New password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (mode == "register") {
                    Text("Select two different recovery questions and provide private answers.")
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
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (busy) CircularProgressIndicator()
                Button(enabled = deviceVerified && !busy, onClick = {
                    val emailValue = email
                    val pwValue = password
                    val newValue = replacement
                    val input = NativeCredentials.Enrollment(email, first, last,
                        pwValue, q1, answer1, q2, answer2)
                    busy = true
                    message = ""
                    scope.launch {
                        try {
                            when (mode) {
                                "register" -> {
                                    val success = onEnroll(input)
                                    message = if (success) "Account created. Verify device and sign in."
                                        else "Account could not be created. Existing vaults are never overwritten."
                                    if (success) mode = "login"
                                }
                                "rotation" -> {
                                    val success = onRotation(emailValue, pwValue, newValue)
                                    message = if (success) "Password changed. Verify device to sign in."
                                        else "Password change denied. The existing vault was preserved."
                                    if (success) mode = "login"
                                }
                                else -> {
                                    message = when (onLogin(emailValue, pwValue)) {
                                        NativeAccountRepository.SignInResult.UNLOCKED -> ""
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
                            answer1 = ""
                            answer2 = ""
                            busy = false
                        }
                    }
                }) {
                    Text(when (mode) {
                        "register" -> "Create private vault"
                        "rotation" -> "Change password"
                        else -> "Unlock private vault"
                    })
                }
                TextButton(enabled = !busy, onClick = {
                    mode = if (mode == "register") "login" else "register"
                    password = ""; replacement = ""; answer1 = ""; answer2 = ""; message = ""
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
                Text("Forgot password? Recovery is not yet enabled. No vault will be reset or deleted.",
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