package `in`.growurdhan.model

/**
 * P37/E16: pure policy fed only by a future security-reviewed Play verifier.
 * The production gateway is deliberately unconfigured until genuine Billing
 * signatures, account linkage, revocation and acknowledgement can be checked.
 * Local purchase flags / receipts are NEVER authoritative.
 */
object NativeCommercialEntitlements {
    const val MANUAL = "gud_lifetime"
    const val GMAIL = "gud_gmail"
    const val UPGRADE = "gud_gmail_upgrade"
    const val SUPPORT = "gud_support_annual"
    const val TRIAL_DAYS = 30L
    const val DAY_MILLIS = 86_400_000L
    private val products = setOf(MANUAL, GMAIL, UPGRADE, SUPPORT)

    enum class State { ACTIVE, PENDING, REVOKED, REFUNDED }
    data class Purchase(val sku: String, val state: State,
        val acknowledged: Boolean, val verifiedForActivePlayAccount: Boolean,
        val expiresAtMillis: Long? = null)
    data class VerifiedPlaySnapshot(val purchases: List<Purchase>,
        val verificationSucceeded: Boolean, val accountVerified: Boolean,
        val verifiedAtMillis: Long? = null, val validUntilMillis: Long? = null)

    /** Only a real Play install attestation can establish this trial anchor. */
    data class TrialEvidence(val verifiedPlayInstall: Boolean,
        val startMillis: Long, val latestObservedMillis: Long)
    data class Access(val canRead: Boolean, val canUseGmail: Boolean,
        val paidLifetime: Boolean, val supportActive: Boolean,
        val trialActive: Boolean, val reason: String)

    interface BillingGateway {
        /** A raw receipt, test flag or locally persisted boolean is not a verification. */
        fun verifiedSnapshot(): VerifiedPlaySnapshot
    }

    object UnconfiguredGateway : BillingGateway {
        override fun verifiedSnapshot(): VerifiedPlaySnapshot =
            VerifiedPlaySnapshot(emptyList(), verificationSucceeded = false,
                accountVerified = false)
    }

    fun evaluate(nowMillis: Long, trial: TrialEvidence?, play: VerifiedPlaySnapshot): Access {
        val validTrial = trial != null && trial.verifiedPlayInstall &&
            trial.startMillis >= 0L && trial.latestObservedMillis >= trial.startMillis &&
            nowMillis >= trial.latestObservedMillis && nowMillis >= trial.startMillis &&
            nowMillis - trial.startMillis < TRIAL_DAYS * DAY_MILLIS

        // No verified Play response means no purchase entitlement, even if
        // cached/unverified local claims contain known SKU names.
        // A claim can be authoritative only inside the verifier's short-lived
        // account-bound lease. A cached, future-dated or expired assertion is
        // never an entitlement, even if its purchase state still says ACTIVE.
        val issued = play.verifiedAtMillis
        val expires = play.validUntilMillis
        val trustedNow = nowMillis >= 0L && issued != null && issued >= 0L &&
            expires != null && expires > issued &&
            nowMillis >= issued && nowMillis < expires
        val live = if (play.verificationSucceeded && play.accountVerified && trustedNow) {
            // Conflicting records for the same SKU must not be resolved in favor
            // of the positive one; the verifier must reconcile them first.
            play.purchases.groupBy { it.sku }.filter { (sku, records) ->
                sku in products && records.size == 1 && records.single().let {
                    it.state == State.ACTIVE && it.acknowledged &&
                        it.verifiedForActivePlayAccount &&
                        (sku != SUPPORT || (it.expiresAtMillis != null &&
                            nowMillis < it.expiresAtMillis))
                }
            }.keys
        } else emptySet()
        val manualPaid = MANUAL in live || GMAIL in live
        val gmailPaid = GMAIL in live || (MANUAL in live && UPGRADE in live)
        val support = manualPaid && SUPPORT in live
        val canRead = validTrial || manualPaid
        return Access(
            canRead = canRead,
            canUseGmail = validTrial || gmailPaid,
            paidLifetime = manualPaid,
            supportActive = support,
            trialActive = validTrial,
            reason = when {
                validTrial -> "Verified Play trial"
                manualPaid -> "Verified Play lifetime"
                else -> "Trial expired or Play entitlement unavailable"
            }
        )
    }
}
