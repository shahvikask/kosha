package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.time.Instant
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

    private fun verifiedEnroll(repository: NativeAccountRepository,
        input: NativeCredentials.Enrollment,
        at: String = Instant.now().toString()): String =
        repository.enrollVerified(input,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(
                NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, at, true), at)

    @Test fun interruptedRegistrationCodeCanBeSafelyReplacedAfterFreshAuthentication() {
        val context = sandbox()
        val input = NativeCredentials.Enrollment("delivery@example.org", "Delivery",
            "Owner", "Strong!Pass123", "school", "First School", "pet", "My Cat")
        val first = repository(context)
        // Simulate the Activity losing its response after the encrypted enrollment
        // committed: no plaintext code is written to persistent storage.
        verifiedEnroll(first, input)
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

    @Test fun backgroundRequiresFreshAdmissionAndPasswordBeforeVaultReopens() {
        val ctx = sandbox()
        val gate = admitted()
        val repo = NativeAccountRepository(ctx, gate)
        val input = NativeCredentials.Enrollment("resume@example.org", "Resume",
            "Owner", "Strong!Pass123", "school", "School", "pet", "Cat")
        verifiedEnroll(repo, input)
        assertTrue(repo.login(input.email, input.password))
        repo.withUnlockedVault(input.email) { Unit }
        gate.onStop()
        repo.onBackground()
        gate.onStart()
        assertThrows(IllegalStateException::class.java) {
            repo.withUnlockedVault(input.email) { Unit }
        }
        assertFalse(repo.login(input.email, input.password))
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        assertThrows(IllegalStateException::class.java) {
            repo.withUnlockedVault(input.email) { Unit }
        }
        assertTrue(repo.login(input.email, input.password))
        repo.withUnlockedVault(input.email) { Unit }
        repo.onBackground()
    }

    @Test fun recreatedRepositoryRequiresFreshDeviceAndPasswordAfterSimulatedProcessDeath() {
        val ctx = sandbox()
        val input = NativeCredentials.Enrollment("restart@example.org", "Restart",
            "Owner", "Strong!Pass123", "school", "School", "pet", "Cat")
        val first = repository(ctx)
        verifiedEnroll(first, input)
        assertTrue(first.login(input.email, input.password))
        first.withUnlockedVault(input.email) { Unit }
        first.onBackground()
        // Dropping the first instance mimics process-owned session destruction;
        // this is NOT a force-stop/kill of the Android process or hardware proof.
        val freshGate = NativeDeviceAdmission()
        freshGate.onStart()
        val restarted = NativeAccountRepository(ctx, freshGate)
        assertFalse(restarted.login(input.email, input.password))
        assertThrows(IllegalStateException::class.java) {
            restarted.withUnlockedVault(input.email) { Unit }
        }
        assertTrue(freshGate.begin())
        assertFalse(freshGate.complete(false))
        assertFalse(restarted.login(input.email, input.password))
        assertTrue(freshGate.begin())
        assertTrue(freshGate.complete(true))
        assertThrows(IllegalStateException::class.java) {
            restarted.withUnlockedVault(input.email) { Unit }
        }
        assertFalse(restarted.login(input.email, "incorrect"))
        assertTrue(restarted.login(input.email, input.password))
        restarted.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM vault_account", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
        }
    }

    @Test fun interruptionRollsBackEncryptedPortfolioBoundaryWrite() {
        val ctx = sandbox()
        val gate = admitted()
        val secure = NativeAccountRepository(ctx, gate)
        val input = NativeCredentials.Enrollment("interrupted@example.org", "Interrupted",
            "Owner", "Strong!Pass123", "school", "School", "pet", "Cat")
        verifiedEnroll(secure, input)
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
        assertThrows(IllegalStateException::class.java) { verifiedEnroll(secure, input) }
        assertFalse(secure.login(input.email, input.password))
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        verifiedEnroll(secure, input)
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
        verifiedEnroll(primary, input)
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
        verifiedEnroll(repository, input)
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
        val code = verifiedEnroll(repo, input)
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
        val code = verifiedEnroll(first, input)
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
        verifiedEnroll(repo, input, "2020-01-01T00:00:00Z")
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
            verifiedEnroll(service, input.copy(password = "weak"), "2026-10-18T00:00:00Z")
        }
        assertFalse(ctx.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        verifiedEnroll(service, input, "2026-10-18T00:00:00Z")
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
        assertThrows(IllegalStateException::class.java) { verifiedEnroll(reopened, input) }
        reopened.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT password_hash FROM vault_account", null).use {
                assertTrue(it.moveToFirst())
                assertNotEquals(input.password, it.getString(0))
            }
        }
    }

    /** An invalid CAS-derived snapshot must not write to the REAL encrypted vault. */
    @Test fun malformedCasSnapshotCannotWriteToSqlCipherOnInspectOrApproval() {
        val ctx = sandbox()
        val email = "cas-negative@example.org"
        val password = "Strong!Pass123"
        val accounts = repository(ctx)
        verifiedEnroll(accounts, NativeCredentials.Enrollment(
            email, "Cas", "Negative", password, "school", "School", "pet", "Cat"))
        assertTrue(accounts.login(email, password))
        val imports = NativeStatementImportRepository(accounts)
        val source = "%PDF-synthetic-not-financial".toByteArray(Charsets.UTF_8)
        val forged = `in`.growurdhan.model.NativeStatementCsv.Snapshot(
            "0".repeat(64), source.size, "ABCDE1234F", "2025-02-28",
            emptyList())
        fun financialCounts(): List<Long> = accounts.withUnlockedVault(email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event", "holding").map { table ->
                db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            }
        }
        val before = financialCounts()
        assertThrows(IllegalArgumentException::class.java) {
            imports.inspectSnapshot(email, forged, source)
        }
        assertEquals(before, financialCounts())
        val fakeReview = NativeStatementImportRepository.Review(
            "0".repeat(64), "2025-02-28", emptyList(), "0".repeat(64), false)
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(email, "fake.pdf", source, forged, "CAS_PDF",
                fakeReview, true)
        }
        assertEquals(before, financialCounts())
        // Missing explicit owner approval must be refused even when a CAS
        // snapshot and claimed preview are supplied; no encrypted writes.
        val noConsent = assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(email, "source.pdf", source, forged,
                "CAS_PDF", fakeReview, false)
        }
        assertEquals("Import requires explicit reviewed approval", noConsent.message)
        assertEquals(before, financialCounts())
        // The SQLCipher approval boundary must reject source-type relabeling
        // before inspecting any potentially fabricated snapshot contents.
        val mislabeledBytes = ByteArray(64) { 0x41.toByte() }
        val mislabeledSha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(mislabeledBytes).joinToString("") { "%02x".format(it) }
        val mislabeled = `in`.growurdhan.model.NativeStatementCsv.Snapshot(
            mislabeledSha, mislabeledBytes.size, "ABCDE1234F", "2025-02-28",
            emptyList())
        val relabelError = assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(email, "source.pdf", mislabeledBytes, mislabeled,
                "CAS_PDF", fakeReview, true)
        }
        assertEquals("CAS approval requires original PDF container",
            relabelError.message)
        assertEquals(before, financialCounts())
        // A well-bound PDF-shaped snapshot must still refuse an unknown
        // PAN/ISIN/folio. No holding may be created by statement import.
        val candidateBytes = ("%PDF-1.4\\n" + "not-a-real-financial-fixture".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val candidateSha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes).joinToString("") { "%02x".format(it) }
        val position = `in`.growurdhan.model.NativeStatementCsv.Position(
            "ABCDE1234F", "2025-02-28", "INE000000001", "unknown-folio",
            100000000L, 10000L, 10000L)
        val unknownPosition = `in`.growurdhan.model.NativeStatementCsv.Snapshot(
            candidateSha, candidateBytes.size, "ABCDE1234F", "2025-02-28",
            listOf(position))
        assertThrows(IllegalArgumentException::class.java) {
            imports.inspectSnapshot(email, unknownPosition, candidateBytes)
        }
        assertEquals(before, financialCounts())
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(email, "candidate.pdf", candidateBytes,
                unknownPosition, "CAS_PDF", fakeReview, true)
        }
        assertEquals(before, financialCounts())
        accounts.onBackground()
    }


}