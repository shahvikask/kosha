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


    @Test fun ownerIbjaReferenceEncryptedRestartValidationAndDeletion() {
        val context = sandbox()
        val initial = accounts(context)
        val store = NativeUserSettingsRepository(initial)
        assertNull(store.readIbja(enrollment.email))
        assertThrows(IllegalArgumentException::class.java) {
            store.saveIbja(enrollment.email, "2025-06-01", "12345.6789")
        }
        store.save(enrollment.email, NativeUserSettingsRepository.Preferences(
            ibjaReference = NativeUserSettingsRepository.IbjaReference.OWNER_REVIEW_REQUIRED))
        for (badRate in listOf("", "0", "-2", "1.00001", "01", "1e6")) {
            assertThrows(Exception::class.java) {
                store.saveIbja(enrollment.email, "2025-06-01", badRate)
            }
        }
        assertThrows(Exception::class.java) {
            store.saveIbja(enrollment.email,
                java.time.LocalDate.now().plusDays(1).toString(), "12345.6789")
        }
        val entry = store.saveIbja(enrollment.email, "2025-06-01", "12345.6789")
        assertEquals(123456789L, entry.rupeesPerTenGramsMinor)
        assertEquals(entry, store.readIbja(enrollment.email))
        initial.onBackground()
        assertThrows(Exception::class.java) { store.readIbja(enrollment.email) }
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(enrollment.email, enrollment.password))
        val restored = NativeUserSettingsRepository(reopened)
        assertEquals(entry, restored.readIbja(enrollment.email))
        reopened.withUnlockedVault(enrollment.email) { db ->
            db.execSQL("UPDATE setting SET value='INVALID' " +
                "WHERE key='native.prices.ibja.owner_reference'")
        }
        assertThrows(Exception::class.java) { restored.readIbja(enrollment.email) }
        restored.clearIbja(enrollment.email)
        assertNull(restored.readIbja(enrollment.email))
        reopened.onBackground()
        val latest = NativeAccountRepository(context, gate())
        assertTrue(latest.login(enrollment.email, enrollment.password))
        assertNull(NativeUserSettingsRepository(latest).readIbja(enrollment.email))
    }

    @Test fun encryptedAppearanceAndRbiIbjaPoliciesSurviveRestartAndRejectCorruption() {
        val ctx = sandbox()
        val first = accounts(ctx)
        val settings = NativeUserSettingsRepository(first)
        assertEquals(NativeUserSettingsRepository.Preferences(), settings.read(enrollment.email))
        assertFalse(NativeUserSettingsRepository.Appearance.LIGHT.useDark(true))
        assertTrue(NativeUserSettingsRepository.Appearance.DARK.useDark(false))
        assertTrue(NativeUserSettingsRepository.Appearance.SYSTEM.useDark(true))
        val selected = NativeUserSettingsRepository.Preferences(
            NativeUserSettingsRepository.Appearance.DARK,
            NativeUserSettingsRepository.SgbReference.RBI_REFERENCE_REQUIRED,
            NativeUserSettingsRepository.IbjaReference.OWNER_REVIEW_REQUIRED
        )
        settings.save(enrollment.email, selected)
        assertEquals(selected, settings.read(enrollment.email))
        first.onBackground()
        assertThrows(Exception::class.java) { settings.read(enrollment.email) }
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(enrollment.email, enrollment.password))
        val restored = NativeUserSettingsRepository(reopened)
        assertEquals(selected, restored.read(enrollment.email))
        reopened.withUnlockedVault(enrollment.email) { db ->
            db.execSQL("UPDATE setting SET value='UNTRUSTED' " +
                "WHERE key='native.privacy.appearance'")
        }
        assertThrows(IllegalArgumentException::class.java) {
            restored.read(enrollment.email)
        }
        reopened.withUnlockedVault(enrollment.email) { db ->
            db.execSQL("UPDATE setting SET value='DARK' " +
                "WHERE key='native.privacy.appearance'")
        }
        restored.save(enrollment.email, NativeUserSettingsRepository.Preferences())
        reopened.onBackground()
        val final = NativeAccountRepository(ctx, gate())
        assertTrue(final.login(enrollment.email, enrollment.password))
        assertEquals(NativeUserSettingsRepository.Preferences(),
            NativeUserSettingsRepository(final).read(enrollment.email))
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
        assertTrue(text.contains("network_upload=disabled"))
        assertTrue(text.contains("raw_diagnostic_logs_retained=false"))
        assertTrue(text.contains("support_text=owner_initiated_copy_only"))
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
