package `in`.growurdhan.app.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No device credential or password is created in these deterministic-clock tests. */
class VaultSessionGateTest {
    @Test fun oldCredentialChallengeCannotAuthorizeNewSessionResults() {
        val gate = VaultSessionGate()
        gate.grantDeviceCredential()
        val original = gate.credentialEpoch()
        assertTrue(gate.isCurrentSession(original))
        assertTrue(gate.isCurrentApproval(original))
        gate.lock()
        assertFalse(gate.isCurrentSession(original))
        gate.grantDeviceCredential()
        assertTrue(gate.isUnlocked())
        assertFalse(gate.isCurrentSession(original))
        assertFalse(gate.isCurrentApproval(original))
        assertTrue(gate.isCurrentSession(gate.credentialEpoch()))
    }

    @Test fun oldFinancialApprovalExpiresBeforeGeneralUnlock() {
        val gate = VaultSessionGate()
        var now = 10_000L
        gate.useClockForTest { now }
        gate.grantDeviceCredential()
        val revision = gate.credentialEpoch()
        now += 30_000
        assertTrue(gate.isCurrentSession(revision))
        assertFalse(gate.isCurrentApproval(revision))
    }

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

    @Test fun successfulCredentialChallengesAdvanceRepositorySessionRevision() {
        val gate = VaultSessionGate()
        val before = gate.sessionRevision()
        gate.grantDeviceCredential()
        val first = gate.sessionRevision()
        assertTrue(first > before)
        gate.lock()
        assertFalse(gate.isUnlocked())
        gate.grantDeviceCredential()
        assertTrue(gate.sessionRevision() > first)
        assertTrue(gate.isUnlocked())
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
