package `in`.growurdhan.app.presentation

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import `in`.growurdhan.core.model.VaultStatus
import `in`.growurdhan.core.model.VaultHealth
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate).also {
            it.workDispatcher = StandardTestDispatcher(testScheduler)
        }
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
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate).also {
            it.workDispatcher = StandardTestDispatcher(testScheduler)
        }
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

    @Test fun startingNewBackupPickerWipesPasswordEvenBeforeCancelledJobStarts() = runTest {
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val repo = mockk<VaultRepository>()
        val vm = PortableRecoveryViewModel(mockk<Context>(), repo, gate)
        vm.offerPickedDocument(PortableRecoveryMode.EXPORT)
        gate.grantDeviceCredential()
        val password = "synthetic-only-test-password".toCharArray()

        vm.executeAfterCredential(PortableRecoveryMode.EXPORT, mockk<Uri>(), password)
        // Do NOT advance the scheduler: cancellation precedes coroutine start.
        assertTrue(vm.state.value is PortableRecoveryUiState.Working)
        vm.offerPickedDocument(PortableRecoveryMode.RESTORE)
        assertTrue(password.all { it == '\u0000' })
        advanceUntilIdle()

        assertTrue(vm.state.value is PortableRecoveryUiState.EnterPassphrase)
        coVerify(exactly = 0) { repo.status() }
        coVerify(exactly = 0) { repo.exportEncryptedBackup(any()) }
    }

    @Test fun completedExportFromOldChallengeIsNotConfirmedInNewSession() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val uri = mockk<Uri>()
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val output = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray) {
                super.write(b)
                gate.lock()
                gate.grantDeviceCredential()
            }
        }
        every { resolver.openOutputStream(uri, "wt") } returns output
        coEvery { repo.status() } returns VaultStatus(true, false, 3)
        coEvery { repo.openOrCreate() } returns VaultStatus(true, true, 3)
        val ciphertext = ByteArray(64) { 7 }
        coEvery { repo.exportEncryptedBackup(any()) } returns ciphertext
        val vm = PortableRecoveryViewModel(context, repo, gate).also {
            it.workDispatcher = StandardTestDispatcher(testScheduler)
        }
        vm.offerPickedDocument(PortableRecoveryMode.EXPORT)
        gate.grantDeviceCredential()
        val password = "synthetic-only-test-password".toCharArray()
        vm.executeAfterCredential(PortableRecoveryMode.EXPORT, uri, password)
        advanceUntilIdle()

        assertEquals(PortableRecoveryUiState.Empty, vm.state.value)
        assertTrue(ciphertext.all { it == 0.toByte() })
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 1) { repo.exportEncryptedBackup(any()) }
    }

    @Test fun oldRestoreResultCannotClaimSuccessUnderReplacementChallenge() = runTest {
        val repo = mockk<VaultRepository>()
        val gate = VaultSessionGate().also { it.grantDeviceCredential() }
        val uri = mockk<Uri>()
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val encrypted = "GUDNBACK".toByteArray(Charsets.US_ASCII) + ByteArray(60)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(encrypted)
        coEvery { repo.status() } returns VaultStatus(false, false, 0)
        coEvery { repo.openOrCreate() } returns VaultStatus(true, true, 3)
        coEvery { repo.restoreEncryptedBackup(any(), any()) } coAnswers {
            gate.lock()
            gate.grantDeviceCredential()
        }
        coEvery { repo.health() } returns VaultHealth("ok", 0, 1, 0, 0)
        val vm = PortableRecoveryViewModel(context, repo, gate).also {
            it.workDispatcher = StandardTestDispatcher(testScheduler)
        }
        vm.offerPickedDocument(PortableRecoveryMode.RESTORE)
        gate.grantDeviceCredential()
        val password = "synthetic-only-test-password".toCharArray()
        vm.executeAfterCredential(PortableRecoveryMode.RESTORE, uri, password)
        advanceUntilIdle()

        assertEquals(PortableRecoveryUiState.Empty, vm.state.value)
        assertTrue(password.all { it == '\u0000' })
        coVerify(exactly = 1) { repo.restoreEncryptedBackup(any(), any()) }
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
