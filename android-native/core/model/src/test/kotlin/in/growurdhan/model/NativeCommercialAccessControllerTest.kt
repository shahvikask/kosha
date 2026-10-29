package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeCommercialAccessControllerTest {
    @Test fun productionDefaultAndOfflineFailureCannotAuthorize() {
        val now = 31L * NativeCommercialEntitlements.DAY_MILLIS
        val disabled = NativeCommercialAccessController().current(now)
        assertFalse(disabled.canRead)
        assertFalse(disabled.canUseGmail)
        assertFalse(disabled.paidLifetime)
        val bad = NativeCommercialAccessController(
            object : NativeCommercialEntitlements.BillingGateway {
                override fun verifiedSnapshot(): NativeCommercialEntitlements.VerifiedPlaySnapshot =
                    error("Play unavailable")
            },
            object : NativeCommercialAccessController.VerifiedInstallSource {
                override fun trialEvidence(): NativeCommercialEntitlements.TrialEvidence? =
                    error("Play install verification unavailable")
            })
        assertEquals(disabled, bad.current(now))
    }

    @Test fun onlyVerifiedPortsCanModelManualAndGmailEntitlements() {
        val verified = object : NativeCommercialEntitlements.BillingGateway {
            override fun verifiedSnapshot() =
                NativeCommercialEntitlements.VerifiedPlaySnapshot(listOf(
                    NativeCommercialEntitlements.Purchase(
                        NativeCommercialEntitlements.GMAIL,
                        NativeCommercialEntitlements.State.ACTIVE,
                        acknowledged = true,
                        verifiedForActivePlayAccount = true)),
                    verificationSucceeded = true, accountVerified = true,
                    verifiedAtMillis = 0L,
                    validUntilMillis = 100L * NativeCommercialEntitlements.DAY_MILLIS)
        }
        val status = NativeCommercialAccessController(verified).current(
            31 * NativeCommercialEntitlements.DAY_MILLIS)
        assertTrue(status.canRead)
        assertTrue(status.canUseGmail)
        assertTrue(status.paidLifetime)
        assertFalse(status.supportActive)
        val unverified = object : NativeCommercialEntitlements.BillingGateway {
            override fun verifiedSnapshot() = verified.verifiedSnapshot().copy(
                accountVerified = false)
        }
        assertFalse(NativeCommercialAccessController(unverified).current(0L).canRead)
    }
}
