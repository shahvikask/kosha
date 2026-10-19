package `in`.growurdhan.redevelopment

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit two-step admission: Android device credential, then native vault password.
 * No financial data or existing vault is displayed by the locked app.
 */
class MainActivity : ComponentActivity() {
    private lateinit var accounts: NativeAccountRepository
    private var deviceVerified by mutableStateOf(false)
    private var unlocked by mutableStateOf(false)
    private var deviceMessage by mutableStateOf("")
    private var lifecycleEpoch = 0
    private var restoreActive by mutableStateOf(false)
    private var restoreStaged by mutableStateOf(false)
    private var restorePickerPending = false
    private val restoreFlow by lazy { NativeRestoreFlow(applicationContext) }
    private val restorePicker = registerForActivityResult(OpenDocument()) { uri ->
        if (!restorePickerPending) return@registerForActivityResult
        restorePickerPending = false
        if (uri != null) restoreStaged = runCatching {
            restoreFlow.stage(uri); true
        }.getOrDefault(false)
        // SAF returned from another Activity: require a completely new OS challenge.
        lockCompletely(preserveStagedArchive = true)
    }
    private val admission = NativeDeviceAdmission()

    private val osChallenge = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        deviceVerified = admission.complete(result.resultCode == Activity.RESULT_OK)
        deviceMessage = if (deviceVerified) "" else "Device authentication was not completed"
        if (deviceVerified && restoreStaged) restoreActive = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        accounts = NativeAccountRepository(applicationContext, admission)
        enableEdgeToEdge()
        setContent {
            GudTheme {
                if (restoreActive && deviceVerified) {
                    NativeRestoreScreen(
                        deviceVerified = deviceVerified,
                        staged = restoreStaged,
                        previewAction = { proof ->
                            if (!admission.valid() || !deviceVerified) null
                            else withContext(Dispatchers.IO) {
                                runCatching { restoreFlow.preview(proof) }.getOrNull()
                            }
                        },
                        publishAction = { proof, preview ->
                            if (!admission.valid() || !deviceVerified || unlocked) false
                            else {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { restoreFlow.publish(proof, preview); true }
                                        .getOrDefault(false)
                                }
                                lockCompletely()
                                restoreActive = false
                                ok
                            }
                        },
                        selectEncryptedFile = {
                            if (admission.valid() && deviceVerified) {
                                restorePickerPending = true
                                restorePicker.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                            }
                        },
                        onBack = { restoreActive = false; restoreStaged = false; lockCompletely() }
                    )
                } else NativeAuthenticationScreen(
                    deviceVerified = deviceVerified,
                    unlocked = unlocked,
                    deviceMessage = deviceMessage,
                    onDeviceChallenge = { requestDeviceChallenge() },
                    onEnroll = { input ->
                        if (!deviceVerified || !admission.valid()) null else {
                            val atStart = lifecycleEpoch
                            val code = withContext(Dispatchers.IO) {
                                runCatching { accounts.enroll(input) }.getOrNull()
                            }
                            unlocked = false
                            if (atStart == lifecycleEpoch && deviceVerified) code else null
                        }
                    },
                    onLogin = { email, password ->
                        if (!deviceVerified || !admission.valid()) NativeAccountRepository.SignInResult.DENIED
                        else {
                            val atStart = lifecycleEpoch
                            val result = withContext(Dispatchers.IO) {
                                accounts.loginResult(email, password)
                            }
                            if (atStart != lifecycleEpoch || !deviceVerified) {
                                accounts.lock()
                                NativeAccountRepository.SignInResult.DENIED
                            } else {
                                unlocked = result == NativeAccountRepository.SignInResult.UNLOCKED
                                result
                            }
                        }
                    },
                    onRotation = { email, old, replacement ->
                        if (!deviceVerified || !admission.valid()) false else {
                            val atStart = lifecycleEpoch
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    accounts.rotatePassword(email, old, replacement)
                                    true
                                }.getOrDefault(false)
                            }
                            accounts.lock()
                            unlocked = false
                            deviceVerified = false
                            result && lifecycleEpoch == atStart
                        }
                    },
                    onRecover = { email, code, q1, a1, q2, a2, newPass ->
                        if (!deviceVerified || !admission.valid()) false else {
                            val atStart = lifecycleEpoch
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    accounts.recoverWithCode(email, code, q1, a1, q2, a2, newPass)
                                    true
                                }.getOrDefault(false)
                            }
                            lockCompletely()
                            result && lifecycleEpoch == atStart
                        }
                    },
                    onReplaceRecoveryCode = { email ->
                        if (!deviceVerified || !admission.valid() || !unlocked) null else {
                            val atStart = lifecycleEpoch
                            val code = withContext(Dispatchers.IO) {
                                runCatching { accounts.replaceRecoveryCode(email) }.getOrNull()
                            }
                            if (atStart == lifecycleEpoch && unlocked) code else null
                        }
                    },
                    onLock = { lockCompletely() },
                    onStartRestore = {
                        if (deviceVerified && admission.valid() && !unlocked) {
                            restoreActive = true
                        }
                    }
                )
            }
        }
    }

    private fun requestDeviceChallenge() {
        lockCompletely(preserveStagedArchive = restoreStaged)
        val guard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!guard.isDeviceSecure) {
            deviceMessage = "Set an Android screen lock before accessing the vault"
            return
        }
        val challenge = guard.createConfirmDeviceCredentialIntent(
            "GrowUrDhan private vault", "Confirm your device credential")
        if (challenge == null) {
            deviceMessage = "Device authentication unavailable"
            return
        }
        if (!admission.begin()) {
            deviceMessage = "Device authentication unavailable"
            return
        }
        try { osChallenge.launch(challenge) }
        catch (_: Exception) {
            admission.cancel()
            deviceMessage = "Device authentication unavailable"
        }
    }

    private fun lockCompletely(preservePendingChallenge: Boolean = false,
                               preserveStagedArchive: Boolean = false) {
        if (::accounts.isInitialized) accounts.lock()
        unlocked = false
        deviceVerified = false
        restoreActive = false
        if (!preserveStagedArchive && !restorePickerPending) restoreStaged = false
        if (!preservePendingChallenge) admission.cancel()
    }

    override fun onStart() {
        super.onStart()
        admission.onStart()
    }

    override fun onStop() {
        admission.onStop()
        lifecycleEpoch++
        lockCompletely(preservePendingChallenge = true,
            preserveStagedArchive = restorePickerPending || restoreStaged)
        if (::accounts.isInitialized) accounts.onBackground()
        super.onStop()
    }
}