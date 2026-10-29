package `in`.growurdhan.model

/**
 * P37/E16 production trust boundary. No disk-stored receipt or local flag can
 * create a purchase or trial. Real Play verification remains unconfigured.
 */
class NativeCommercialAccessController(
    private val billing: NativeCommercialEntitlements.BillingGateway =
        NativeCommercialEntitlements.UnconfiguredGateway,
    private val install: VerifiedInstallSource = UnconfiguredInstall
) {
    interface VerifiedInstallSource {
        /** Implement only with independently verified Play install evidence. */
        fun trialEvidence(): NativeCommercialEntitlements.TrialEvidence?
    }
    object UnconfiguredInstall : VerifiedInstallSource {
        override fun trialEvidence(): NativeCommercialEntitlements.TrialEvidence? = null
    }

    fun current(nowMillis: Long): NativeCommercialEntitlements.Access {
        val deny = NativeCommercialEntitlements.evaluate(nowMillis, null,
            NativeCommercialEntitlements.UnconfiguredGateway.verifiedSnapshot())
        if (nowMillis < 0L) return deny
        return try {
            NativeCommercialEntitlements.evaluate(nowMillis,
                install.trialEvidence(), billing.verifiedSnapshot())
        } catch (_: Exception) {
            // Revoked provider, timeout, missing Play, malformed response: deny.
            deny
        }
    }
}
