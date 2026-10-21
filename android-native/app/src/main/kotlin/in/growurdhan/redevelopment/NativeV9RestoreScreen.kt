package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.model.NativeRegistrationProof
import kotlinx.coroutines.launch

/** No automatic resume, no saved-state credentials, no live-vault merge. */
@Composable
internal fun NativeV9RestoreScreen(
    deviceVerified: Boolean,
    staged: Boolean,
    selectEncryptedFile: () -> Unit,
    previewAction: suspend (RestoreIdentityGate.Credentials, CharArray,
        NativeRegistrationProof.Consent) -> NativeV9NewDeviceRestore.Review?,
    publishAction: suspend (RestoreIdentityGate.Credentials, CharArray,
        NativeRegistrationProof.Consent, NativeV9NewDeviceRestore.Review) -> Boolean,
    forensicAction: suspend (RestoreIdentityGate.Credentials, CharArray,
        NativeRegistrationProof.Consent) -> NativeV9RecoveryInspector.Inspection?,
    resumePreviewAction: suspend (RestoreIdentityGate.Credentials, CharArray,
        NativeRegistrationProof.Consent) -> NativeV9ManualResume.Offer? = { _, _, _ -> null },
    resumePublishAction: suspend (RestoreIdentityGate.Credentials, CharArray,
        NativeRegistrationProof.Consent, NativeV9ManualResume.Offer) -> Boolean =
        { _, _, _, _ -> false },
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var archivePassword by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var q1 by remember { mutableStateOf("city") }
    var a1 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf("pet") }
    var a2 by remember { mutableStateOf("") }
    var acceptedAt by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<NativeV9NewDeviceRestore.Review?>(null) }
    var publishApproved by remember { mutableStateOf(false) }
    var inspectApproved by remember { mutableStateOf(false) }
    var forensicState by remember {
        mutableStateOf<NativeV9RecoveryInspector.State?>(null)
    }
    var resumeOffer by remember { mutableStateOf<NativeV9ManualResume.Offer?>(null) }
    var resumeApproved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun invalidate(keepForensicHint: Boolean = false) {
        review = null; publishApproved = false; message = ""
        // The read-only forensic hint survives secret re-entry, but neither
        // a verified offer nor owner approval can survive any input change.
        if (!keepForensicHint) forensicState = null
        resumeOffer = null; resumeApproved = false
        inspectApproved = false
    }
    fun proof() = RestoreIdentityGate.Credentials(
        email, accountPassword, pan, dob, q1, a1, q2, a2)
    fun consent() = NativeRegistrationProof.Consent(
        NativeFrozenTerms.VERSION, NativeFrozenTerms.TEXT_ID,
        requireNotNull(acceptedAt), scrolledToEnd = true)
    fun clearSecrets() {
        accountPassword = ""; archivePassword = ""; a1 = ""; a2 = ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Native GUDNAT01 recovery — new empty device only",
            style = MaterialTheme.typography.titleLarge)
        Text("Offline, identity verified, no merge or replacement of existing encrypted data.")
        OutlinedButton(enabled = !busy, onClick = { clearSecrets(); onBack() }) {
            Text("Back to recovery options")
        }
        Button(enabled = deviceVerified && !busy && !staged,
            onClick = selectEncryptedFile) {
            Text(if (staged) "Encrypted native archive staged"
                else "Choose native encrypted GUDNAT01 backup")
        }
        Text("Interrupted recovery: read-only forensic review can verify artifacts but " +
            "cannot clean files, resume publication, or approve an existing vault.")
        if (deviceVerified) {
            OutlinedTextField(email, { email = it; invalidate() },
                modifier = Modifier.testTag("nativeRecoveryEmail"),
                label = { Text("Original account email") })
            OutlinedTextField(accountPassword, { accountPassword = it; invalidate(keepForensicHint = true) },
                modifier = Modifier.testTag("nativeRecoveryAccountPassword"),
                label = { Text("Original account password") },
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(archivePassword, { archivePassword = it; invalidate(keepForensicHint = true) },
                modifier = Modifier.testTag("nativeRecoveryArchivePassword"),
                label = { Text("Separate backup password") },
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pan, { pan = it; invalidate() },
                modifier = Modifier.testTag("nativeRecoveryPan"),
                label = { Text("Full original PAN") })
            OutlinedTextField(dob, { dob = it; invalidate() },
                modifier = Modifier.testTag("nativeRecoveryDob"),
                label = { Text("Original date of birth (YYYY-MM-DD)") })
            OutlinedTextField(q1, { q1 = it; invalidate() },
                label = { Text("First security question ID") })
            OutlinedTextField(a1, { a1 = it; invalidate(keepForensicHint = true) },
                modifier = Modifier.testTag("nativeRecoveryAnswer1"),
                label = { Text("First answer") },
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(q2, { q2 = it; invalidate() },
                label = { Text("Second security question ID") })
            OutlinedTextField(a2, { a2 = it; invalidate(keepForensicHint = true) },
                modifier = Modifier.testTag("nativeRecoveryAnswer2"),
                label = { Text("Second answer") },
                visualTransformation = PasswordVisualTransformation())
            NativeTermsGate(acceptedAt, enabled = !busy,
                onAccept = { acceptedAt = it; invalidate() },
                onDecline = { clearSecrets(); onBack() })
            val ready = !busy && acceptedAt != null && email.isNotBlank() &&
                accountPassword.isNotBlank() && archivePassword.length >= 12 &&
                pan.isNotBlank() && dob.isNotBlank() &&
                q1.isNotBlank() && q2.isNotBlank() &&
                a1.isNotBlank() && a2.isNotBlank()
            Button(enabled = staged && ready, onClick = {
                busy = true
                scope.launch {
                    try {
                        review = previewAction(proof(), archivePassword.toCharArray(), consent())
                        publishApproved = false
                        message = if (review != null)
                            "Original native source authenticated. Review the row count below."
                        else "Native archive or independent owner credentials rejected."
                    } catch (_: Exception) {
                        invalidate()
                        message = "Native archive or owner proof rejected; no vault changed."
                    } finally { busy = false }
                }
            }) { Text("Verify native identity and preview") }
            review?.let { approvedReview ->
                Text("Authenticated financial snapshot: " + approvedReview.rows +
                    " original rows. A permanent one-use recovery claim will be created.")
                Text("Only an unused vault can be published; never merge or overwrite.")
                Checkbox(checked = publishApproved,
                    onCheckedChange = { publishApproved = it && ready },
                    modifier = Modifier.testTag("nativeRecoveryPublishApproval"))
                Text("I approve creating one new private vault from this exact " +
                    "authenticated source and its current Terms.")
                Button(enabled = ready && publishApproved, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val completed = publishAction(proof(),
                                archivePassword.toCharArray(), consent(),
                                approvedReview.copy(ownerApproved = true))
                            message = if (completed)
                                "Native vault recovered. Reauthenticate before opening it."
                            else "Recovery was denied; encrypted evidence was preserved."
                        } catch (_: Exception) {
                            message = "Recovery blocked. Encrypted evidence preserved."
                        } finally {
                            review = null; publishApproved = false
                            clearSecrets(); busy = false
                        }
                    }
                }) { Text("Confirm one-time native recovery") }
            }
            Checkbox(checked = inspectApproved, onCheckedChange = { inspectApproved = it },
                modifier = Modifier.testTag("nativeRecoveryInspectApproval"))
            Text("I approve READ-ONLY inspection of interrupted recovery evidence. " +
                "This does not authorize publication or cleanup.")
            OutlinedButton(enabled = ready && inspectApproved, onClick = {
                busy = true
                scope.launch {
                    try {
                        val outcome = forensicAction(proof(),
                            archivePassword.toCharArray(), consent())
                        forensicState = outcome?.state
                        resumeOffer = null
                        resumeApproved = false
                        message = if (outcome == null)
                            "No verified recovery evidence; no files were changed."
                        else "Forensic state: " + outcome.state +
                            ", verified rows: " + outcome.rows +
                            ". Inspection alone cannot publish or resume recovery."
                    } catch (_: Exception) {
                        message = "Forensic evidence rejected. No repair or retry was performed."
                    } finally {
                        review = null; publishApproved = false
                        inspectApproved = false; clearSecrets(); busy = false
                    }
                }
            }) { Text("Inspect interrupted restore (read-only)") }
            if (forensicState == NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE) {
                Text("An unpublished candidate has passed forensic inspection. " +
                    "Manual resumption requires fresh owner proof and a second approval.")
                OutlinedButton(enabled = ready && !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            resumeOffer = resumePreviewAction(proof(),
                                archivePassword.toCharArray(), consent())
                            resumeApproved = false
                            message = if (resumeOffer == null)
                                "Native candidate re-verification failed; no files changed."
                            else "Reverified " + resumeOffer!!.rows +
                                " typed source rows. Review before irreversible publication."
                        } catch (_: Exception) {
                            resumeOffer = null
                            message = "Manual resume preparation blocked; evidence preserved."
                        } finally { busy = false }
                    }
                }) { Text("Reverify native candidate for manual resumption") }
                resumeOffer?.let { offer ->
                    Checkbox(checked = resumeApproved,
                        onCheckedChange = { resumeApproved = it && ready },
                        modifier = Modifier.testTag("nativeRecoveryResumeApproval"))
                    Text("I explicitly approve publishing this same verified, previously " +
                        "claimed encrypted candidate. Never overwrite an existing vault.")
                    Button(enabled = ready && resumeApproved, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val ok = resumePublishAction(proof(),
                                    archivePassword.toCharArray(), consent(),
                                    offer.copy(ownerApproved = true))
                                message = if (ok)
                                    "Manual native recovery completed. Reauthenticate to open it."
                                else "Manual resumption denied; existing artifacts preserved."
                            } catch (_: Exception) {
                                message = "Manual resumption blocked; evidence preserved."
                            } finally {
                                resumeOffer = null; resumeApproved = false
                                forensicState = null; clearSecrets(); busy = false
                            }
                        }
                    }) { Text("Approve manual native recovery publication") }
                }
            }
        }
        if (message.isNotEmpty()) Text(message)
    }
}
