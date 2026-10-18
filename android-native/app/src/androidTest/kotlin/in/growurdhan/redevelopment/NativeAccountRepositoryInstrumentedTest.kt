package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAccountRepositoryInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m25-account-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }


    private fun admitted(): NativeDeviceAdmission =
        NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }

    /** Synthetic Android admission for encrypted instrumentation; not a device credential test. */
    private fun repository(ctx: Context,
        clock: () -> Long = System::currentTimeMillis): NativeAccountRepository =
        NativeAccountRepository(ctx, admitted(), clock)

    @Test fun interruptedRegistrationCodeCanBeSafelyReplacedAfterFreshAuthentication() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("delivery@example.org", "Delivery",
            "Owner", "Strong!Pass123", "school", "First School", "pet", "My Cat")
        val first = repository(context)
        // Simulate the Activity losing its response after the encrypted enrollment
        // committed: no plaintext code is written to persistent storage.
        first.enroll(input)
        first.onBackground()
        val restarted = repository(context)
        assertTrue(restarted.login(input.email, input.password))
        val replacement = restarted.replaceRecoveryCode(input.email)
        assertTrue(replacement.startsWith("GUD-"))
        restarted.onBackground()
        restarted.recoverWithCode(input.email, replacement, "school", "First School",
            "pet", "My Cat", "NewStrong!345")
        assertFalse(repository(context).login(input.email, input.password))
        assertTrue(repository(context).login(input.email, "NewStrong!345"))
    }

    @Test fun interruptionRollsBackEncryptedPortfolioBoundaryWrite() {
        val ctx = sandbox()
        val gate = admitted()
        val secure = NativeAccountRepository(ctx, gate)
        val input = NativeCredentials.Enrollment("interrupted@example.org", "Interrupted",
            "Owner", "Strong!Pass123", "school", "School", "pet", "Cat")
        secure.enroll(input)
        assertTrue(secure.login(input.email, input.password))
        assertThrows(IllegalStateException::class.java) {
            secure.withUnlockedVault(input.email) { db ->
                db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                    arrayOf<Any>("m25.interrupted.test", "must-roll-back"))
                gate.onStop()
            }
        }
        assertThrows(IllegalStateException::class.java) {
            secure.withUnlockedVault(input.email) { Unit }
        }
        gate.onStart()
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        assertTrue(secure.login(input.email, input.password))
        secure.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM setting WHERE key='m25.interrupted.test'",
                null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
        secure.onBackground()
        assertTrue(NativeAccountRepository(ctx, admitted()).login(input.email, input.password))
    }

    @Test fun repositoryRequiresOsAdmissionForPasswordAndVaultOperations() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("device@example.org", "Device", "Owner",
            "Strong!Pass123", "school", "First School", "pet", "My Cat")
        val gate = NativeDeviceAdmission()
        gate.onStart()
        val secure = NativeAccountRepository(context, gate)
        assertThrows(IllegalStateException::class.java) { secure.enroll(input) }
        assertFalse(secure.login(input.email, input.password))
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        secure.enroll(input)
        assertTrue(secure.login(input.email, input.password))
        gate.onStop()
        assertThrows(IllegalStateException::class.java) {
            secure.withUnlockedVault(input.email) { Unit }
        }
        assertFalse(secure.login(input.email, input.password))
        assertThrows(IllegalStateException::class.java) {
            secure.rotatePassword(input.email, input.password, "NewStrong!345")
        }
        assertThrows(IllegalStateException::class.java) {
            secure.recoverWithCode(input.email, "invalid", "school", "First School",
                "pet", "My Cat", "NewStrong!345")
        }
        gate.onStart()
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        assertTrue(secure.login(input.email, input.password))
    }

    @Test fun passwordRotationInOtherInstanceImmediatelyRevokesStaleSession() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("revoked@example.org", "Revoked", "Owner",
            "Strong!Pass123", "school", "First School", "pet", "My Cat")
        val primary = repository(context)
        primary.enroll(input)
        assertTrue(primary.login(input.email, input.password))
        primary.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM ledger_event", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }
        val other = repository(context)
        other.rotatePassword(input.email, input.password, "NewStrong!345")
        assertThrows(IllegalStateException::class.java) {
            primary.withUnlockedVault(input.email) { Unit }
        }
        assertThrows(IllegalStateException::class.java) {
            primary.replaceRecoveryCode(input.email)
        }
        assertFalse(primary.login(input.email, input.password))
        assertTrue(primary.login(input.email, "NewStrong!345"))
        primary.onBackground()
        assertThrows(IllegalStateException::class.java) {
            primary.withUnlockedVault(input.email) { Unit }
        }
        assertTrue(repository(context).login(input.email, "NewStrong!345"))
    }

    @Test fun olderAccountWithoutRecoveryCodeCannotResetAndCanProvisionAfterLogin() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("legacy@example.org", "Legacy", "Owner",
            "Strong!Pass123", "school", "First School", "pet", "My Cat")
        val repository = repository(context)
        repository.enroll(input)
        assertTrue(repository.login(input.email, input.password))
        repository.withUnlockedVault(input.email) { db ->
            db.execSQL("DELETE FROM setting WHERE key='m25.auth.recovery_code_verifier'")
        }
        repository.onBackground()
        assertThrows(IllegalArgumentException::class.java) {
            repository.recoverWithCode(input.email, "GUD-unprovisioned",
                "school", "First School", "pet", "My Cat", "NewStrong!345")
        }
        assertTrue(repository.login(input.email, input.password))
        val code = repository.replaceRecoveryCode(input.email)
        assertTrue(code.startsWith("GUD-"))
        repository.onBackground()
        repository.recoverWithCode(input.email, code, "school", "First School",
            "pet", "My Cat", "NewStrong!345")
        assertFalse(repository.login(input.email, input.password))
        assertTrue(repository(context).login(input.email, "NewStrong!345"))
    }

    @Test fun recoveryCodeRequiresOriginalAnswersAndIsSingleUseAcrossEncryptedRestart() {
        val ctx = sandbox()
        val input = NativeCredentials.Enrollment("recovery@example.org", "Recovery", "Owner",
            "Strong!Pass123", "school", "School Name", "pet", "Cat")
        val repo = repository(ctx)
        val code = repo.enroll(input)
        assertTrue(code.startsWith("GUD-"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.recoverWithCode(input.email, "bad", "school", "School Name",
                "pet", "Cat", "NewStrong!345")
        }
        assertTrue(repo.login(input.email, input.password))
        repo.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.auth.recovery_code_verifier'",
                null).use { c ->
                assertTrue(c.moveToFirst())
                assertFalse(c.getString(0).contains(code))
            }
            db.rawQuery("SELECT COUNT(*) FROM ledger_event", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
        }
        repo.onBackground()
        assertThrows(IllegalArgumentException::class.java) {
            repo.recoverWithCode(input.email, code, "school", "incorrect",
                "pet", "Cat", "NewStrong!345")
        }
        assertTrue(repo.login(input.email, input.password))
        repo.onBackground()
        repo.recoverWithCode(input.email, code, "school", "school name",
            "pet", "cat", "NewStrong!345")
        assertFalse(repo.login(input.email, input.password))
        assertTrue(repo.login(input.email, "NewStrong!345"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.recoverWithCode(input.email, code, "school", "school name",
                "pet", "cat", "Another!Password456")
        }
        val reopened = repository(ctx)
        assertTrue(reopened.login(input.email, "NewStrong!345"))
        reopened.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM setting WHERE key='m25.auth.recovery_code_verifier'",
                null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
        }
    }

    @Test fun durableLoginAndRecoveryThrottleSurvivesRestartAndClockRollback() {
        val ctx = sandbox()
        val base = System.currentTimeMillis()
        var now = base
        val clock = { now }
        val input = NativeCredentials.Enrollment("throttled@example.org", "Throttled", "Owner",
            "Strong!Pass123", "school", "School Name", "pet", "Cat")
        val first = repository(ctx, clock)
        val code = first.enroll(input)
        repeat(4) { assertFalse(first.login(input.email, "bad-password")) }
        assertThrows(IllegalArgumentException::class.java) {
            first.recoverWithCode(input.email, "wrong", "school", "School Name",
                "pet", "Cat", "NewStrong!345")
        }
        val restart = repository(ctx, clock)
        assertFalse(restart.login(input.email, input.password))
        now -= 10_000L
        assertFalse(restart.login(input.email, input.password))
        now = base + 30L * 60L * 1000L + 1L
        assertTrue(restart.login(input.email, input.password))
        restart.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.auth.failure_state'",
                null).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.getString(0).startsWith("0:"))
            }
        }
        restart.onBackground()
        restart.recoverWithCode(input.email, code, "school", "School Name",
            "pet", "Cat", "NewStrong!345")
        assertTrue(repository(ctx, clock).login(input.email, "NewStrong!345"))
    }

    @Test fun expiredNativePasswordRequiresTransactionalRotationAndReopen() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("rotating@example.org", "Owner", "One",
            "Strong!Pass123", "school", "First School", "pet", "Cat")
        val repo = repository(context)
        repo.enroll(input, "2020-01-01T00:00:00Z")
        assertEquals(NativeAccountRepository.SignInResult.ROTATION_REQUIRED,
            repo.loginResult(input.email, input.password))
        assertThrows(IllegalStateException::class.java) {
            repo.withUnlockedVault(input.email) { Unit }
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.rotatePassword(input.email, "wrong", "NewStrong!345")
        }
        assertEquals(NativeAccountRepository.SignInResult.ROTATION_REQUIRED,
            repo.loginResult(input.email, input.password))
        repo.rotatePassword(input.email, input.password, "NewStrong!345")
        assertFalse(repo.login(input.email, input.password))
        assertTrue(repo.login(input.email, "NewStrong!345"))
        repo.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM ledger_event", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }
        repo.onBackground()
        assertThrows(IllegalStateException::class.java) {
            repo.withUnlockedVault(input.email) { Unit }
        }
        assertTrue(repository(context).login(input.email, "NewStrong!345"))
    }

    @Test fun encryptedRegistrationRequiresPasswordForActualNativeVaultAccess() {
        val ctx = sandbox()
        val input = NativeCredentials.Enrollment("primary@example.org", "Primary", "Owner",
            "Strong!Pass123", "school", "School Name", "pet", "Cat")
        val service = repository(ctx)
        assertThrows(IllegalArgumentException::class.java) {
            service.enroll(input.copy(password = "weak"), "2026-10-18T00:00:00Z")
        }
        assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        service.enroll(input, "2026-10-18T00:00:00Z")
        assertThrows(IllegalStateException::class.java) {
            service.withUnlockedVault(input.email) { Unit }
        }
        assertFalse(service.login(input.email, "incorrect"))
        assertTrue(service.login(input.email, input.password))
        service.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM vault_account", null).use {
                assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0))
            }
            db.rawQuery("SELECT display_name FROM entity WHERE is_primary=1", null).use {
                assertTrue(it.moveToFirst()); assertEquals("Primary Owner", it.getString(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM ledger_event", null).use {
                assertTrue(it.moveToFirst()); assertEquals(0L, it.getLong(0))
            }
        }
        service.onBackground()
        assertThrows(IllegalStateException::class.java) {
            service.withUnlockedVault(input.email) { Unit }
        }
        val reopened = repository(ctx)
        assertTrue(reopened.login(input.email, input.password))
        assertThrows(IllegalStateException::class.java) { reopened.enroll(input) }
        reopened.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT password_hash FROM vault_account", null).use {
                assertTrue(it.moveToFirst())
                assertNotEquals(input.password, it.getString(0))
            }
        }
    }
}