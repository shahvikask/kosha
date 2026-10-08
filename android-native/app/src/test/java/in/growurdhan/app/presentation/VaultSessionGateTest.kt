package `in`.growurdhan.app.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No device credential or password is created in these deterministic-clock tests. */
class VaultSessionGateTest {
    @Test fun requiresSuccessfulCredentialAndExpiresAfterFiveMinutes() {
        var now = 1_000L
        val session = VaultSessionGate()
        session.useClockForTest { now }
        assertFalse(session.isUnlocked())
        assertFalse(session.canCommitFinancialChanges())
        session.grantDeviceCredential()
        assertTrue(session.isUnlocked())
        now += 5 * 60 * 1000L - 1
        assertTrue(session.isUnlocked())
        now += 1
        assertFalse(session.isUnlocked())
        assertFalse(session.canCommitFinancialChanges())
    }

    @Test fun financialApprovalNeedsCredentialWithinThirtySeconds() {
        var now = 50_000L
        val session = VaultSessionGate()
        session.useClockForTest { now }
        session.grantDeviceCredential()
        now += 29_999L
        assertTrue(session.canCommitFinancialChanges())
        now += 1
        assertFalse(session.canCommitFinancialChanges())
        assertTrue(session.isUnlocked())
        session.grantDeviceCredential()
        assertTrue(session.canCommitFinancialChanges())
    }

    @Test fun backgroundLockAlwaysRevokesPriorCredential() {
        val session = VaultSessionGate()
        session.grantDeviceCredential()
        session.lock()
        assertFalse(session.isUnlocked())
        assertFalse(session.canCommitFinancialChanges())
    }

    @Test fun reversedMonotonicClockFailsClosed() {
        var now = 2_000L
        val session = VaultSessionGate()
        session.useClockForTest { now }
        session.grantDeviceCredential()
        now -= 100
        assertFalse(session.isUnlocked())
        now = 2_000L
        assertFalse(session.isUnlocked())
    }
}
