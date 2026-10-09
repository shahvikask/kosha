package `in`.growurdhan.domain.vault

/**
 * Deny-by-default, volatile authorization boundary enforced at the production
 * repository binding, not just in UI / ViewModels. The device's credential is
 * distinct from SQLCipher's Keystore encryption key and backup password.
 */
interface VaultAuthorization {
    fun requireUnlocked()
    fun requireRecentApproval()
}
