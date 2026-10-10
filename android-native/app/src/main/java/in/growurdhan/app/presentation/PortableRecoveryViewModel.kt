package `in`.growurdhan.app.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.growurdhan.domain.vault.VaultRepository
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PortableRecoveryMode { EXPORT, RESTORE }

sealed interface PortableRecoveryUiState {
    data object Empty : PortableRecoveryUiState
    /** Only operation name lives in StateFlow; no password/URI or bytes are retained. */
    data class EnterPassphrase(
        val mode: PortableRecoveryMode,
        /** Challenge epoch at document selection; approval must be newer. */
        val stagedCredentialEpoch: Long = 0L,
    ) : PortableRecoveryUiState
    data object Working : PortableRecoveryUiState
    data class Completed(val mode: PortableRecoveryMode) : PortableRecoveryUiState
    data class Error(val message: String) : PortableRecoveryUiState
}

/**
 * Security-sensitive portable backup action coordinator. Activity owns SAF URI
 * selection and the system credential callback. The encrypted transfer is done
 * only after a newly verified credential; no plaintext financial data touches SAF.
 */
@HiltViewModel
class PortableRecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: VaultRepository,
    private val session: VaultSessionGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow<PortableRecoveryUiState>(PortableRecoveryUiState.Empty)
    val state: StateFlow<PortableRecoveryUiState> = mutableState
    private var operation: Job? = null
    @Volatile private var generation = 0L
    // Owned explicitly so cancellation before coroutine start can still zero
    // the password rather than leaving the only reference in a suspended job.
    private var inFlightPassword: CharArray? = null

    fun offerPickedDocument(mode: PortableRecoveryMode) {
        operation?.cancel()
        ++generation
        mutableState.value = PortableRecoveryUiState.EnterPassphrase(
            mode, session.credentialEpoch(),
        )
    }

    fun failInput() {
        mutableState.value = PortableRecoveryUiState.Error(
            "Use a backup password of at least 12 characters. For export, both entries must match.",
        )
    }

    fun executeAfterCredential(mode: PortableRecoveryMode, uri: Uri, password: CharArray) {
        val selected = mutableState.value as? PortableRecoveryUiState.EnterPassphrase
        if (selected == null || selected.mode != mode ||
            session.credentialEpoch() <= selected.stagedCredentialEpoch ||
            !session.canCommitFinancialChanges() || password.size !in 12..1024
        ) {
            password.fill('\u0000')
            mutableState.value = PortableRecoveryUiState.Error(
                "Re-select the backup and confirm your device credential before continuing.",
            )
            return
        }
        operation?.cancel()
        inFlightPassword?.fill('\u0000')
        inFlightPassword = password
        val current = ++generation
        val authenticatedRevision = session.credentialEpoch()
        mutableState.value = PortableRecoveryUiState.Working
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val status = vault.status()
                    if (mode == PortableRecoveryMode.EXPORT) {
                        // An export must NEVER create an empty vault implicitly.
                        check(status.initialized) { "No existing book to back up." }
                        vault.openOrCreate()
                        val encrypted = vault.exportEncryptedBackup(password)
                        try {
                            // Encryption/key derivation can outlive the original
                            // approval. Never write to a SAF document afterwards.
                            check(current == generation &&
                                session.canCommitFinancialChanges() &&
                                session.credentialEpoch() == authenticatedRevision
                            ) { "Backup export approval expired before writing the document." }
                            context.contentResolver.openOutputStream(uri, "wt")?.use {
                                it.write(encrypted)
                                it.flush()
                            } ?: error("Selected document cannot be written.")
                        } finally {
                            encrypted.fill(0)
                        }
                    } else {
                        // Restore never replaces an existing book. Repository
                        // requires every whitelisted table empty and rolls back
                        // the SQL transaction if decryption/validation fails.
                        vault.openOrCreate()
                        val encrypted = context.contentResolver.openInputStream(uri)?.use {
                            PortableBackupBytes.read(it)
                        } ?: error("Selected backup cannot be read.")
                        try {
                            vault.restoreEncryptedBackup(password, encrypted)
                            val health = vault.health()
                            check(health.foreignKeyViolations == 0 && health.integrity == "ok") {
                                "The recovered book failed integrity checks."
                            }
                        } finally {
                            encrypted.fill(0)
                        }
                    }
                }
                if (current == generation && session.isUnlocked()) {
                    mutableState.value = PortableRecoveryUiState.Completed(mode)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (current == generation) {
                    // No source pathname, password, PAN or crypto error reaches UI/logs.
                    mutableState.value = PortableRecoveryUiState.Error(
                        "Backup operation could not be completed. Existing financial records were not replaced.",
                    )
                }
            } finally {
                password.fill('\u0000')
                if (inFlightPassword === password) inFlightPassword = null
            }
        }
    }

    fun clearSensitiveState() {
        ++generation
        operation?.cancel()
        operation = null
        inFlightPassword?.fill('\u0000')
        inFlightPassword = null
        mutableState.value = PortableRecoveryUiState.Empty
    }

    override fun onCleared() {
        clearSensitiveState()
        super.onCleared()
    }
}

/** Bounded ciphertext read, header sanity check, and prompt wiping of buffers. */
object PortableBackupBytes {
    const val MAX_CIPHERTEXT_BYTES = 34 * 1024 * 1024
    private val marker = "GUDNBACK".toByteArray(Charsets.US_ASCII)

    fun read(stream: InputStream, maxBytes: Int = MAX_CIPHERTEXT_BYTES): ByteArray {
        require(maxBytes in 64..MAX_CIPHERTEXT_BYTES)
        var buffer = ByteArray(minOf(16 * 1024, maxBytes))
        var count = 0
        var stalled = 0
        try {
            while (true) {
                if (count == buffer.size) {
                    if (count >= maxBytes) {
                        require(stream.read() == -1) { "Portable backup is too large." }
                        break
                    }
                    val more = ByteArray(minOf(maxBytes, buffer.size * 2))
                    buffer.copyInto(more, endIndex = count)
                    buffer.fill(0)
                    buffer = more
                }
                val n = stream.read(buffer, count, buffer.size - count)
                if (n == -1) break
                if (n == 0) {
                    require(++stalled <= 16) { "Backup stream stalled." }
                    continue
                }
                stalled = 0
                count += n
            }
            require(count >= 64 && buffer.copyOfRange(0, marker.size).contentEquals(marker)) {
                "Selected document is not a GrowUrDhan portable backup."
            }
            return buffer.copyOf(count)
        } finally {
            buffer.fill(0)
        }
    }
}
