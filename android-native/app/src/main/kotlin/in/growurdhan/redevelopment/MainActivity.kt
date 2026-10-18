package `in`.growurdhan.redevelopment

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
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

    private val osChallenge = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        deviceVerified = result.resultCode == Activity.RESULT_OK
        deviceMessage = if (deviceVerified) "" else "Device authentication was not completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        accounts = NativeAccountRepository(applicationContext)
        enableEdgeToEdge()
        setContent {
            GudTheme {
                NativeAuthenticationScreen(
                    deviceVerified = deviceVerified,
                    unlocked = unlocked,
                    deviceMessage = deviceMessage,
                    onDeviceChallenge = { requestDeviceChallenge() },
                    onEnroll = { input -> 
                        if (!deviceVerified) false else {
                            val atStart = lifecycleEpoch
                            val success = withContext(Dispatchers.IO) {
                                runCatching { accounts.enroll(input); true }.getOrDefault(false)
                            }
                            unlocked = false
                            deviceVerified = false
                            success && lifecycleEpoch == atStart
                        }
                    },
                    onLogin = { email, password ->
                        if (!deviceVerified) NativeAccountRepository.SignInResult.DENIED
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
                        if (!deviceVerified) false else {
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
                    onLock = { lockCompletely() }
                )
            }
        }
    }

    private fun requestDeviceChallenge() {
        lockCompletely()
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
        osChallenge.launch(challenge)
    }

    private fun lockCompletely() {
        if (::accounts.isInitialized) accounts.lock()
        unlocked = false
        deviceVerified = false
    }

    override fun onStop() {
        lifecycleEpoch++
        lockCompletely()
        if (::accounts.isInitialized) accounts.onBackground()
        super.onStop()
    }
}