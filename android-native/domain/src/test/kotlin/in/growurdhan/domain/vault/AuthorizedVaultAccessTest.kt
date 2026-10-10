package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.VaultStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizedVaultAccessTest {
    private class Gate : VaultAuthorization {
        var unlocked = true
        var revision = 1L
        fun confirmAgain() {
            unlocked = true
            revision++
        }
        override fun sessionRevision(): Long = revision
        override fun requireUnlocked() {
            check(unlocked) { "Synthetic session expired" }
        }
        override fun requireRecentApproval() = requireUnlocked()
    }

    @Test fun lockedCallDoesNotOpenOrCloseRepository() = runTest {
        val delegate = mockk<VaultRepository>()
        val gate = Gate().also { it.unlocked = false }
        try {
            AuthorizedVaultAccess(delegate, gate).openOrCreate()
            fail("Locked opening must fail")
        } catch (_: IllegalStateException) {
            // Precondition is checked before any SQLCipher call.
        }
        coVerify(exactly = 0) { delegate.openOrCreate() }
        coVerify(exactly = 0) { delegate.close() }
    }

    @Test fun unlockedOpenReturnsStatusWithoutClosing() = runTest {
        val delegate = mockk<VaultRepository>()
        val status = VaultStatus(true, true, 3)
        coEvery { delegate.openOrCreate() } returns status
        val actual = AuthorizedVaultAccess(delegate, Gate()).openOrCreate()
        assertEquals(status, actual)
        coVerify(exactly = 1) { delegate.openOrCreate() }
        coVerify(exactly = 0) { delegate.close() }
    }

    @Test fun revocationWhileOpenIsSuspendedClosesPhysicalConnection() = runTest {
        val delegate = mockk<VaultRepository>()
        val pending = CompletableDeferred<VaultStatus>()
        coEvery { delegate.openOrCreate() } coAnswers { pending.await() }
        coEvery { delegate.close() } returns Unit
        val gate = Gate()
        supervisorScope {
            val opening = async { AuthorizedVaultAccess(delegate, gate).openOrCreate() }
            runCurrent()
            gate.unlocked = false
            pending.complete(VaultStatus(true, true, 3))
            val failure = runCatching { opening.await() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }
        coVerify(exactly = 1) { delegate.close() }
    }

    @Test fun newlyVerifiedSessionCannotRescueAnOpeningFromPriorSession() = runTest {
        val delegate = mockk<VaultRepository>()
        val pending = CompletableDeferred<VaultStatus>()
        coEvery { delegate.openOrCreate() } coAnswers { pending.await() }
        coEvery { delegate.close() } returns Unit
        val gate = Gate()
        supervisorScope {
            val opening = async { AuthorizedVaultAccess(delegate, gate).openOrCreate() }
            runCurrent()
            gate.unlocked = false
            gate.confirmAgain() // Valid session now, but not the originating challenge.
            pending.complete(VaultStatus(true, true, 3))
            val failure = runCatching { opening.await() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }
        coVerify(exactly = 1) { delegate.close() }
    }

    @Test fun newlyVerifiedSessionCannotRevealPriorSessionsPrivateRead() = runTest {
        val gate = Gate()
        val pending = CompletableDeferred<String>()
        supervisorScope {
            val reading = async {
                AuthorizedVaultAccess(mockk(), gate).read { pending.await() }
            }
            runCurrent()
            gate.unlocked = false
            gate.confirmAgain()
            pending.complete("Synthetic value from older session")
            assertTrue(runCatching { reading.await() }.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun cancelledOpenStillClosesPhysicalConnection() = runTest {
        val delegate = mockk<VaultRepository>()
        val pending = CompletableDeferred<VaultStatus>()
        coEvery { delegate.openOrCreate() } coAnswers { pending.await() }
        coEvery { delegate.close() } returns Unit
        val gate = Gate()
        val opening = async { AuthorizedVaultAccess(delegate, gate).openOrCreate() }
        runCurrent()
        opening.cancelAndJoin()
        coVerify(exactly = 1) { delegate.close() }
    }

    @Test fun explicitCloseWaitsForAnOpeningToFinish() = runTest {
        val delegate = mockk<VaultRepository>()
        val pendingOpen = CompletableDeferred<VaultStatus>()
        coEvery { delegate.openOrCreate() } coAnswers { pendingOpen.await() }
        coEvery { delegate.close() } returns Unit
        val access = AuthorizedVaultAccess(delegate, Gate())
        val opening = async { access.openOrCreate() }
        runCurrent()
        val closing = async { access.close() }
        runCurrent()

        coVerify(exactly = 0) { delegate.close() }
        pendingOpen.complete(VaultStatus(true, true, 3))
        opening.await()
        closing.await()
        coVerify(exactly = 1) { delegate.close() }
    }

    @Test fun failedOpenCleanupFinishesBeforeNewCredentialCanReopen() = runTest {
        val delegate = mockk<VaultRepository>()
        val pendingOpen = CompletableDeferred<VaultStatus>()
        val pendingClose = CompletableDeferred<Unit>()
        var opens = 0
        coEvery { delegate.openOrCreate() } coAnswers {
            opens++
            if (opens == 1) pendingOpen.await() else VaultStatus(true, true, 3)
        }
        coEvery { delegate.close() } coAnswers { pendingClose.await() }
        val gate = Gate()
        val access = AuthorizedVaultAccess(delegate, gate)
        val oldOpen = async {
            runCatching { access.openOrCreate() }.exceptionOrNull()
        }
        runCurrent()
        gate.unlocked = false
        pendingOpen.complete(VaultStatus(true, true, 3))
        runCurrent()
        gate.confirmAgain()
        val newOpen = async { access.openOrCreate() }
        runCurrent()

        assertEquals(1, opens)
        coVerify(exactly = 1) { delegate.close() }
        pendingClose.complete(Unit)
        assertTrue(oldOpen.await() is IllegalStateException)
        assertEquals(VaultStatus(true, true, 3), newOpen.await())
        assertEquals(2, opens)
    }

    @Test fun readRevocationDiscardsResultInsteadOfReturningPrivateData() = runTest {
        val delegate = mockk<VaultRepository>()
        val gate = Gate()
        val pending = CompletableDeferred<String>()
        supervisorScope {
            val reading = async {
                AuthorizedVaultAccess(delegate, gate).read { pending.await() }
            }
            runCurrent()
            gate.unlocked = false
            pending.complete("Synthetic private value")
            val failure = runCatching { reading.await() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }
    }

    @Test fun authorizedReadReturnsResult() = runTest {
        val access = AuthorizedVaultAccess(mockk(), Gate())
        assertEquals("Synthetic permitted value", access.read { "Synthetic permitted value" })
    }
}
