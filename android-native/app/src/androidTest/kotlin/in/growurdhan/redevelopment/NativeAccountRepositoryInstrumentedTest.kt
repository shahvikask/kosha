package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
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


    @Test fun recoveryCodeRequiresOriginalAnswersAndIsSingleUseAcrossEncryptedRestart() {
        val ctx = sandbox()
        val input = NativeCredentials.Enrollment("recovery@example.org", "Recovery", "Owner",
            "Strong!Pass123", "school", "School Name", "pet", "Cat")
        val repo = NativeAccountRepository(ctx)
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
        val reopened = NativeAccountRepository(ctx)
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
        val first = NativeAccountRepository(ctx, clock)
        val code = first.enroll(input)
        repeat(4) { assertFalse(first.login(input.email, "bad-password")) }
        assertThrows(IllegalArgumentException::class.java) {
            first.recoverWithCode(input.email, "wrong", "school", "School Name",
                "pet", "Cat", "NewStrong!345")
        }
        val restart = NativeAccountRepository(ctx, clock)
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
        assertTrue(NativeAccountRepository(ctx, clock).login(input.email, "NewStrong!345"))
    }

    @Test fun expiredNativePasswordRequiresTransactionalRotationAndReopen() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("rotating@example.org", "Owner", "One",
            "Strong!Pass123", "school", "First School", "pet", "Cat")
        val repo = NativeAccountRepository(context)
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
        assertTrue(NativeAccountRepository(context).login(input.email, "NewStrong!345"))
    }

    @Test fun encryptedRegistrationRequiresPasswordForActualNativeVaultAccess() {
        val ctx = sandbox()
        val input = NativeCredentials.Enrollment("primary@example.org", "Primary", "Owner",
            "Strong!Pass123", "school", "School Name", "pet", "Cat")
        val service = NativeAccountRepository(ctx)
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
        val reopened = NativeAccountRepository(ctx)
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