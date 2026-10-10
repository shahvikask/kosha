package `in`.growurdhan.app.presentation

import android.content.Context
import android.net.Uri
import `in`.growurdhan.core.model.VaultStatus
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import `in`.growurdhan.domain.vault.VaultRepository
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure denial-path tests; no real Android URI or financial data is opened. */
@OptIn(ExperimentalCoroutinesApi::class)
class PortableRecoveryAuthorizationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
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

    @Test fun expiredCredentialDuringPickerCleanupNeverOpensBackupVault() = runTest {
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val repo = mockk<VaultRepository>()
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate)
        vm.offerPickedDocument(PortableRecoveryMode.RESTORE)
        gate.grantDeviceCredential()
        val waitingCleanup = CompletableDeferred<Unit>()
        val password = "synthetic-only-test-password".toCharArray()

        vm.executeAfterCredential(PortableRecoveryMode.RESTORE, mockk<Uri>(), password) {
            waitingCleanup.await()
        }
        runCurrent()
        assertTrue(vm.state.value is PortableRecoveryUiState.Working)
        coVerify(exactly = 0) { repo.status() }
        gate.lock()
        waitingCleanup.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.state.value is PortableRecoveryUiState.Error)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 0) { repo.openOrCreate() }
        coVerify(exactly = 0) { repo.restoreEncryptedBackup(any(), any()) }
    }

    @Test fun completedCleanupAllowsFreshApprovalToReachBackupStatus() = runTest {
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val repo = mockk<VaultRepository>()
        coEvery { repo.status() } returns VaultStatus(false, false, 0)
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate)
        vm.offerPickedDocument(PortableRecoveryMode.EXPORT)
        gate.grantDeviceCredential()
        val waitingCleanup = CompletableDeferred<Unit>()
        val password = "synthetic-only-test-password".toCharArray()

        vm.executeAfterCredential(PortableRecoveryMode.EXPORT, mockk<Uri>(), password) {
            waitingCleanup.await()
        }
        runCurrent()
        coVerify(exactly = 0) { repo.status() }
        waitingCleanup.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.status() }
        coVerify(exactly = 0) { repo.openOrCreate() }
        assertTrue(vm.state.value is PortableRecoveryUiState.Error)
        assertTrue(password.all { it == '\u0000' })
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
