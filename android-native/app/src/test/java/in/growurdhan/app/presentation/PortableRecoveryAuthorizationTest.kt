package `in`.growurdhan.app.presentation

import android.content.Context
import android.net.Uri
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure denial-path tests; no real Android URI or financial data is opened. */
class PortableRecoveryAuthorizationTest {
    @Test fun exportCannotReuseCredentialThatPredatedDocumentSelection() {
        val gate = VaultSessionGate()
        gate.grantDeviceCredential()
        val repo = mockk<VaultRepository>()
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate)
        vm.offerPickedDocument(PortableRecoveryMode.EXPORT)
        val before = gate.credentialEpoch()
        val password = "synthetic-only-test-password".toCharArray()

        vm.executeAfterCredential(PortableRecoveryMode.EXPORT, mockk<Uri>(), password)

        assertEquals(before, gate.credentialEpoch())
        assertTrue(vm.state.value is PortableRecoveryUiState.Error)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { repo.exportEncryptedBackup(any()) }
    }

    @Test fun restoreCannotReuseOldCredentialOrAccessVaultWithoutNewChallenge() {
        val gate = VaultSessionGate()
        gate.grantDeviceCredential()
        val repo = mockk<VaultRepository>()
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate)
        vm.offerPickedDocument(PortableRecoveryMode.RESTORE)
        val password = "synthetic-only-test-password".toCharArray()

        vm.executeAfterCredential(PortableRecoveryMode.RESTORE, mockk<Uri>(), password)

        assertTrue(vm.state.value is PortableRecoveryUiState.Error)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { repo.restoreEncryptedBackup(any(), any()) }
        coVerify(exactly = 0) { repo.openOrCreate() }
    }
}
