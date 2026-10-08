package `in`.growurdhan.app.presentation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ephemeral process-local authorization following Android's secure device credential.
 * Never persists a token, password or receipt. Time uses a monotonic clock.
 *
 * This is an app-UI gate only; backend services must still validate all writes.
 * Final commercial authentication/session design remains a separate security gate.
 */
@Singleton
class VaultSessionGate @Inject constructor() {
    private var nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
    private var verifiedAtMillis: Long? = null
    private val maxSessionAgeMillis = 5 * 60 * 1000L
    private val maxApprovalAgeMillis = 30 * 1000L

    // Test-only monotonic clock injection, no Android framework required.
    internal fun useClockForTest(clock: () -> Long) {
        lock()
        nowMillis = clock
    }

    @Synchronized fun grantDeviceCredential() {
        verifiedAtMillis = nowMillis()
    }

    @Synchronized fun lock() {
        verifiedAtMillis = null
    }

    @Synchronized fun isUnlocked(): Boolean = freshWithin(maxSessionAgeMillis)

    @Synchronized fun canCommitFinancialChanges(): Boolean = freshWithin(maxApprovalAgeMillis)

    private fun freshWithin(maxAge: Long): Boolean {
        val verified = verifiedAtMillis ?: return false
        val elapsed = nowMillis() - verified
        if (elapsed < 0 || elapsed >= maxSessionAgeMillis) {
            verifiedAtMillis = null
            return false
        }
        return elapsed < maxAge
    }
}
