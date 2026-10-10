package `in`.growurdhan.app.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic challenge revisions; no real PDF, credentials or family data. */
class PdfImportGuardTest {
    @Test fun newDeviceChallengeCannotRescuePreviousImport() {
        val session = VaultSessionGate().also { it.grantDeviceCredential() }
        val guard = PdfImportGuard(session)
        val old = guard.begin()
        assertTrue(guard.isCurrent(old))

        session.lock()
        session.grantDeviceCredential()

        assertFalse(guard.isCurrent(old))
        assertTrue(guard.isCurrent(guard.begin()))
    }

    @Test fun explicitCancellationInvalidatesImportEvenWhileStillUnlocked() {
        val session = VaultSessionGate().also { it.grantDeviceCredential() }
        val guard = PdfImportGuard(session)
        val request = guard.begin()

        guard.invalidate()

        assertTrue(session.isUnlocked())
        assertFalse(guard.isCurrent(request))
    }

    @Test fun newPickerSelectionSupersedesOlderImport() {
        val session = VaultSessionGate().also { it.grantDeviceCredential() }
        val guard = PdfImportGuard(session)
        val older = guard.begin()
        val newer = guard.begin()

        assertFalse(guard.isCurrent(older))
        assertTrue(guard.isCurrent(newer))
    }

    @Test fun expiredSessionBlocksCompletedParserResult() {
        var now = 1_000L
        val session = VaultSessionGate().also {
            it.useClockForTest { now }
            it.grantDeviceCredential()
        }
        val guard = PdfImportGuard(session)
        val ticket = guard.begin()
        now += 5 * 60 * 1000L

        assertFalse(guard.isCurrent(ticket))
        assertFalse(session.isUnlocked())
    }
}
