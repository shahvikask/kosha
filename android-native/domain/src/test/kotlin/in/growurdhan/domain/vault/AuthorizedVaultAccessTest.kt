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
