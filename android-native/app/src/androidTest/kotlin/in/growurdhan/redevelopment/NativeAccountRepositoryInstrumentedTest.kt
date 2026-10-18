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