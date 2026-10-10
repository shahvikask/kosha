package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.VaultStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Defense-in-depth for asynchronous calls that cross the process-local device
 * credential boundary. Opening must fail closed if a session expires while
 * SQLCipher is opening, including cancellation of a waiting caller.
 *
 * The backing repository must still serialize connection operations and enforce
 * transaction-level financial integrity. This is not a substitute for authentication.
 */
class AuthorizedVaultAccess(
    private val delegate: VaultRepository,
    private val authorization: VaultAuthorization,
) {
    suspend fun openOrCreate(): VaultStatus {
        // An unauthenticated call cannot create keys, files, or a connection.
        authorization.requireUnlocked()
        val authorizedRevision = authorization.sessionRevision()
        try {
            val status = delegate.openOrCreate()
            // A credential that was valid before a long-running open may no
            // longer be valid when the connection is ready.
            authorization.requireUnlocked()
            check(authorization.sessionRevision() == authorizedRevision) {
                "The verified device session changed during vault opening."
            }
            return status
        } catch (failure: Throwable) {
            // Cancellation of the caller must not strand an open SQLCipher
            // connection. Preserve the original failure if close also fails.
            withContext(NonCancellable) {
                try {
                    delegate.close()
                } catch (closeFailure: Throwable) {
                    if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                }
            }
            throw failure
        }
    }

    /** Never return private rows or review receipts after session expiry. */
    suspend fun <T> read(action: suspend () -> T): T {
        authorization.requireUnlocked()
        val authorizedRevision = authorization.sessionRevision()
        val result = action()
        authorization.requireUnlocked()
        check(authorization.sessionRevision() == authorizedRevision) {
            "The verified device session changed during private vault read."
        }
        return result
    }
}
