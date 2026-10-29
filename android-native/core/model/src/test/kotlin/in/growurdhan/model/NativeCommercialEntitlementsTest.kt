package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeCommercialEntitlementsTest {
    private val policy = NativeCommercialEntitlements
    private val day = policy.DAY_MILLIS
    private fun verified(vararg p: NativeCommercialEntitlements.Purchase) =
        NativeCommercialEntitlements.VerifiedPlaySnapshot(p.toList(), true, true)
    private fun active(sku: String) = NativeCommercialEntitlements.Purchase(
        sku, NativeCommercialEntitlements.State.ACTIVE, true, true)
    private val noTrial = NativeCommercialEntitlements.TrialEvidence(false, 0, 0)

    @Test fun thirtyDayAttestedTrialIsExclusiveAtDeadlineAndRollbackFailsClosed() {
        val trial = NativeCommercialEntitlements.TrialEvidence(true, 10 * day, 15 * day)
        assertTrue(policy.evaluate(15 * day, trial,
            policy.UnconfiguredGateway.verifiedSnapshot()).canUseGmail)
        assertTrue(policy.evaluate(40 * day - 1, trial,
            policy.UnconfiguredGateway.verifiedSnapshot()).canRead)
        assertFalse(policy.evaluate(40 * day, trial,
            policy.UnconfiguredGateway.verifiedSnapshot()).canRead)
        assertFalse(policy.evaluate(14 * day, trial,
            policy.UnconfiguredGateway.verifiedSnapshot()).canRead)
        assertFalse(policy.evaluate(15 * day, noTrial,
            policy.UnconfiguredGateway.verifiedSnapshot()).canRead)
    }

    @Test fun lifetimeAndGmailUpgradeRequireVerifiedPlayAndCorrectSkuCombination() {
        val manual = policy.evaluate(50 * day, noTrial, verified(active(policy.MANUAL)))
        assertTrue(manual.canRead)
        assertFalse(manual.canUseGmail)
        assertFalse(manual.supportActive)
        val gmail = policy.evaluate(50 * day, noTrial, verified(active(policy.GMAIL)))
        assertTrue(gmail.canUseGmail)
        assertTrue(gmail.paidLifetime)
        val upgradeAlone = policy.evaluate(50 * day, noTrial, verified(active(policy.UPGRADE)))
        assertFalse(upgradeAlone.canRead)
        assertFalse(upgradeAlone.canUseGmail)
        val combination = policy.evaluate(50 * day, noTrial,
            verified(active(policy.MANUAL), active(policy.UPGRADE), active(policy.SUPPORT)))
        assertTrue(combination.canUseGmail)
        assertTrue(combination.supportActive)
    }

    @Test fun pendingRefundRevocationAndUnverifiedAccountCannotGrantLicenses() {
        for (state in listOf(NativeCommercialEntitlements.State.PENDING,
            NativeCommercialEntitlements.State.REFUNDED,
            NativeCommercialEntitlements.State.REVOKED)) {
            val purchase = active(policy.GMAIL).copy(state = state)
            assertFalse(policy.evaluate(40 * day, noTrial, verified(purchase)).canRead)
        }
        assertFalse(policy.evaluate(40 * day, noTrial, verified(
            active(policy.GMAIL).copy(acknowledged = false))).canRead)
        assertFalse(policy.evaluate(40 * day, noTrial, verified(
            active(policy.GMAIL).copy(verifiedForActivePlayAccount = false))).canRead)
        assertFalse(policy.evaluate(40 * day, noTrial,
            NativeCommercialEntitlements.VerifiedPlaySnapshot(
                listOf(active(policy.GMAIL)), false, true)).canRead)
        assertFalse(policy.evaluate(40 * day, noTrial,
            NativeCommercialEntitlements.VerifiedPlaySnapshot(
                listOf(active(policy.GMAIL)), true, false)).canRead)
    }

    @Test fun annualSupportRequiresLifetimeAndUnknownSkuDoesNotGrant() {
        val supportOnly = policy.evaluate(40 * day, noTrial, verified(active(policy.SUPPORT)))
        assertFalse(supportOnly.canRead)
        assertFalse(supportOnly.supportActive)
        assertFalse(policy.evaluate(40 * day, noTrial,
            verified(active("unrecognized_product"))).canRead)
        assertFalse(policy.evaluate(40 * day, null,
            policy.UnconfiguredGateway.verifiedSnapshot()).canRead)
    }
}
