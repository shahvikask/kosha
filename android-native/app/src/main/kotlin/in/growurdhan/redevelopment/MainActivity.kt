package `in`.growurdhan.redevelopment

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import java.io.ByteArrayOutputStream
import android.provider.OpenableColumns
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
import `in`.growurdhan.model.NativeRegistrationProof
import `in`.growurdhan.model.NativeDeviceAdmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit two-step admission: Android device credential, then native vault password.
 * No financial data or existing vault is displayed by the locked app.
 */
class MainActivity : ComponentActivity() {
    private lateinit var accounts: NativeAccountRepository
    private lateinit var books: NativeBooksRepository
    private lateinit var statementImports: NativeStatementImportRepository
    private lateinit var interestPayouts: NativeInterestPayoutRepository
    private lateinit var privateDiagnostics: NativePrivateDiagnosticsRepository
    private lateinit var casPdf: NativeCasPdfEngine
    private var stagedPdf by mutableStateOf<ByteArray?>(null)
    private var stagedPdfName by mutableStateOf("")
    private var pdfPickerPending = false
    private fun discardPdf() {
        stagedPdf?.fill(0)
        stagedPdf = null
        stagedPdfName = ""
    }
    private val pdfPicker = registerForActivityResult(OpenDocument()) { uri ->
        if (!pdfPickerPending) return@registerForActivityResult
        pdfPickerPending = false
        discardPdf()
        if (uri != null) {
            stagedPdf = runCatching {
                contentResolver.openInputStream(uri)?.use { source ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        require(out.size().toLong() + n <= 16L * 1024 * 1024)
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
            if (stagedPdf != null) {
                val name = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) cur.getString(0) else null
                    }
                }.getOrNull()
                stagedPdfName = (name ?: "statement.pdf").take(240)
            }
        }
        lockCompletely(preservePdfForFreshLogin = stagedPdf != null)
    }
    // Ephemeral only. SAF backgrounds the Activity, so login+device proof is
    // required again before the owner can preview the selected CSV.
    private var stagedCsv by mutableStateOf<ByteArray?>(null)
    private var stagedCsvFilename by mutableStateOf("")
    private var csvPickerPending = false
    private fun discardCsv() {
        stagedCsv?.fill(0)
        stagedCsv = null
        stagedCsvFilename = ""
    }
    private val csvPicker = registerForActivityResult(OpenDocument()) { uri ->
        if (!csvPickerPending) return@registerForActivityResult
        csvPickerPending = false
        discardCsv()
        if (uri != null) {
            stagedCsv = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val part = ByteArray(8192)
                    while (true) {
                        val n = input.read(part)
                        if (n < 0) break
                        require(out.size().toLong() + n <= 8L * 1024 * 1024) {
                            "Oversized statement file"
                        }
                        out.write(part, 0, n)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
            if (stagedCsv != null) {
                val display = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
                stagedCsvFilename = (display ?: uri.lastPathSegment
                    ?.substringAfterLast('/') ?: "statement.csv").take(240)
            }
        }
        // Never retain admission across a third-party document picker.
        lockCompletely(preserveCsvForFreshLogin = stagedCsv != null)
    }
    private var booksOpen by mutableStateOf(false)
    private var activeEmail by mutableStateOf("")
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
        books = NativeBooksRepository(accounts)
        statementImports = NativeStatementImportRepository(accounts)
        interestPayouts = NativeInterestPayoutRepository(accounts)
        privateDiagnostics = NativePrivateDiagnosticsRepository(accounts)
        casPdf = NativeCasPdfEngine(applicationContext)
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
                        publishAction = { proof, preview, consent ->
                            if (!admission.valid() || !deviceVerified || unlocked) false
                            else {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { restoreFlow.publish(proof, preview, consent); true }
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
                } else if (booksOpen && unlocked && deviceVerified) {
                    NativeBooksScreen(email = activeEmail, books = books,
                        imports = statementImports, payouts = interestPayouts,
                        diagnostics = privateDiagnostics,
                        casPdf = casPdf,
                        pendingPdf = stagedPdf, pendingPdfFilename = stagedPdfName,
                        onSelectPdf = {
                            if (unlocked && admission.valid() && !pdfPickerPending) {
                                pdfPickerPending = true
                                pdfPicker.launch(arrayOf("application/pdf", "*/*"))
                            }
                        },
                        onDiscardPdf = { discardPdf() },
                        pendingCsv = stagedCsv, pendingCsvFilename = stagedCsvFilename,
                        onSelectCsv = {
                            if (unlocked && admission.valid() && !csvPickerPending) {
                                csvPickerPending = true
                                csvPicker.launch(arrayOf("text/csv", "text/plain", "*/*"))
                            }
                        },
                        onDiscardCsv = { discardCsv() },
                        onLock = { lockCompletely() },
                        onReplaceRecovery = { booksOpen = false })
                } else NativeAuthenticationScreen(
                    deviceVerified = deviceVerified,
                    unlocked = unlocked,
                    deviceMessage = deviceMessage,
                    onDeviceChallenge = { requestDeviceChallenge() },
                    onEnroll = { input, identity, consent ->
                        if (!deviceVerified || !admission.valid()) null else {
                            val atStart = lifecycleEpoch
                            val code = withContext(Dispatchers.IO) {
                                runCatching { accounts.enrollVerified(input, identity, consent) }.getOrNull()
                            }
                            unlocked = false
                            if (atStart == lifecycleEpoch && deviceVerified) code else null
                        }
                    },
                    onLogin = { email, password, consent ->
                        if (!deviceVerified || !admission.valid()) NativeAccountRepository.SignInResult.DENIED
                        else {
                            val atStart = lifecycleEpoch
                            val result = withContext(Dispatchers.IO) {
                                accounts.loginResult(email, password, consent)
                            }
                            if (atStart != lifecycleEpoch || !deviceVerified) {
                                accounts.lock()
                                NativeAccountRepository.SignInResult.DENIED
                            } else {
                                unlocked = result == NativeAccountRepository.SignInResult.UNLOCKED
                                activeEmail = if (unlocked) email.trim().lowercase(java.util.Locale.ROOT) else ""
                                booksOpen = false
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
                    onOpenBooks = { if (unlocked && admission.valid() && activeEmail.isNotEmpty()) booksOpen = true },
                    onDeclineTerms = { lockCompletely(); finish() },
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
        lockCompletely(preserveStagedArchive = restoreStaged,
            preserveCsvForFreshLogin = stagedCsv != null,
            preservePdfForFreshLogin = stagedPdf != null)
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
                               preserveStagedArchive: Boolean = false,
                               preserveCsvForFreshLogin: Boolean = false,
                               preservePdfForFreshLogin: Boolean = false) {
        if (::accounts.isInitialized) accounts.lock()
        unlocked = false
        booksOpen = false
        activeEmail = ""
        deviceVerified = false
        restoreActive = false
        if (!preserveStagedArchive && !restorePickerPending) restoreStaged = false
        if (!preserveCsvForFreshLogin && !csvPickerPending) discardCsv()
        if (!preservePdfForFreshLogin && !pdfPickerPending) discardPdf()
        if (!preservePendingChallenge) admission.cancel()
    }

    override fun onStart() {
        super.onStart()
        admission.onStart()
    }

    override fun onDestroy() {
        discardCsv()
        discardPdf()
        super.onDestroy()
    }

    override fun onStop() {
        admission.onStop()
        lifecycleEpoch++
        lockCompletely(preservePendingChallenge = true,
            preserveStagedArchive = restorePickerPending || restoreStaged,
            preserveCsvForFreshLogin = csvPickerPending || stagedCsv != null,
            preservePdfForFreshLogin = pdfPickerPending || stagedPdf != null)
        if (::accounts.isInitialized) accounts.onBackground()
        super.onStop()
    }
}