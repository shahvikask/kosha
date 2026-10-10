package `in`.growurdhan.domain.vault

/**
 * Deny-by-default, volatile authorization boundary enforced at the production
 * repository binding, not just in UI / ViewModels. The device's credential is
 * distinct from SQLCipher's Keystore encryption key and backup password.
 */
interface VaultAuthorization {
    fun requireUnlocked()
    fun requireRecentApproval()

    /**
     * Monotonic revision of a successfully verified device credential.
     * A long-running operation must not complete using a DIFFERENT unlock
     * than the one that originally authorized it.
     *
     * Legacy isolated test guards may use the default. The production
     * VaultSessionGate overrides this with its actual challenge revision.
     */
    fun sessionRevision(): Long = 0L
}
