package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePrivateDiagnosticsInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(base.cacheDir, "diagnostics-" + UUID.randomUUID())
            .also { it.mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(folder, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(folder, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(folder, "files").also { it.mkdirs() }
        }
    }
    private val enrollment = NativeCredentials.Enrollment(
        "private-owner@example.invalid", "Synthetic", "Owner", "Strong!Pass123",
        "school", "Example School", "pet", "Example Pet")
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun accounts(ctx: Context): NativeAccountRepository {
        val repository = NativeAccountRepository(ctx, gate())
        repository.enrollVerified(enrollment,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(repository.login(enrollment.email, enrollment.password))
        return repository
    }

    @Test fun optInDefaultsOffAndOnlyExplicitConsentPermitsHealthProbe() {
        val store = NativePrivateDiagnosticsRepository(accounts(sandbox()))
        assertFalse(store.enabled(enrollment.email))
        assertNull(store.localReport(enrollment.email))
        store.setEnabled(enrollment.email, true)
        assertTrue(store.enabled(enrollment.email))
        val report = requireNotNull(store.localReport(enrollment.email))
        assertEquals(9, report.schemaVersion)
        assertTrue(report.foreignKeysValid)
        assertTrue(report.encryptedLocalOnly)
        assertFalse(report.networkUpload)
        val text = report.redactedText()
        for (secret in listOf("ABCDE1234F", "private-owner@example.invalid",
            "Strong!Pass123", "1990-06-12", "Example School")) {
            assertFalse("Support text must never include personal records",
                text.contains(secret))
        }
        store.setEnabled(enrollment.email, false)
        assertFalse(store.enabled(enrollment.email))
        assertNull(store.localReport(enrollment.email))
    }

    @Test fun consentPersistsEncryptedAcrossLoginAndIsRevocable() {
        val ctx = sandbox()
        val original = accounts(ctx)
        val settings = NativePrivateDiagnosticsRepository(original)
        settings.setEnabled(enrollment.email, true)
        original.onBackground()
        assertThrows(Exception::class.java) {
            settings.localReport(enrollment.email)
        }
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(enrollment.email, enrollment.password))
        val resumed = NativePrivateDiagnosticsRepository(reopened)
        assertTrue(resumed.enabled(enrollment.email))
        resumed.setEnabled(enrollment.email, false)
        assertFalse(resumed.enabled(enrollment.email))
        reopened.onBackground()
        val latest = NativeAccountRepository(ctx, gate())
        assertTrue(latest.login(enrollment.email, enrollment.password))
        assertFalse(NativePrivateDiagnosticsRepository(latest).enabled(enrollment.email))
    }
}
