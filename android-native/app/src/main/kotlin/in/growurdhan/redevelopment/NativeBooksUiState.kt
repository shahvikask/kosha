package `in`.growurdhan.redevelopment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State is bound to the admitted email; it is discarded on vault lock. */
internal class NativeBooksUiState(
    val email: String, val books: NativeBooksRepository,
    val imports: NativeStatementImportRepository,
    val payouts: NativeInterestPayoutRepository,
    val annualCredits: NativeAnnualInterestCreditRepository,
    val diagnostics: NativePrivateDiagnosticsRepository,
    val casPdf: NativeCasPdfEngine,
    val settings: NativeUserSettingsRepository?,
    val privacyConsent: NativePrivacyConsentRepository?,
    val onLock: () -> Unit, val onReplaceRecovery: () -> Unit,
    val onAppearanceChanged: (NativeUserSettingsRepository.Appearance) -> Unit,
    val scope: CoroutineScope
) {
    var snapshot by mutableStateOf<NativeBooksRepository.Snapshot?>(null)
    var busy by mutableStateOf(false)
    var ledgerRevision by mutableStateOf(0)
    var error by mutableStateOf("")
    var exchangeReceipt by mutableStateOf("")
    var preferences by mutableStateOf(NativeUserSettingsRepository.Preferences())
    var preferencesLoaded by mutableStateOf(false)
    var preferencesBusy by mutableStateOf(false)
    var preferencesError by mutableStateOf("")
    var ibjaInput by mutableStateOf<NativeUserSettingsRepository.IbjaInput?>(null)
    var ownerFilter by mutableStateOf("")
    var categoryFilter by mutableStateOf("All")
    var holdingAccountFilter by mutableStateOf("")
    var holdingStatusFilter by mutableStateOf("All")
    var holdingSort by mutableStateOf("Name")
    var search by mutableStateOf("")
    var historyId by mutableStateOf("")
    var history by mutableStateOf<List<NativeBooksRepository.EventRow>>(emptyList())
    var xirrResults by mutableStateOf<Map<String, Double?>>(emptyMap())
    var pendingPdf: ByteArray? = null
    var pendingPdfFilename: String = ""
    var onSelectPdf: () -> Unit = {}
    var onDiscardPdf: () -> Unit = {}
    var pendingCsv: ByteArray? = null
    var pendingCsvFilename: String = ""
    var onSelectCsv: () -> Unit = {}
    var onDiscardCsv: () -> Unit = {}
    var onNavigate: (String) -> Unit = {}

    suspend fun refresh() {
        snapshot = withContext(Dispatchers.IO) { books.read(email) }
    }

    fun persist(task: () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
                refresh()
                ledgerRevision += 1
            } catch (_: Exception) {
                error = "Cannot save. The vault may have locked or this record is invalid. No partial edit was committed."
            } finally { busy = false }
        }
    }
}
