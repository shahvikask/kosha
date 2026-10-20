package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeInterestPayoutPlanner
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated SQLCipher/Keystore data with exclusively synthetic contracts. */
@RunWith(AndroidJUnit4::class)
class NativeInterestPayoutRepositoryInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "reviewed-coupons-" + UUID.randomUUID()).apply {
            mkdirs()
        }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }
    private val enrollment = NativeCredentials.Enrollment(
        "coupons@example.invalid", "Synthetic", "Owner", "Strong!Pass123",
        "school", "Example School", "pet", "Example Pet")
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun create(context: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(context, gate())
        accounts.enrollVerified(enrollment,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(enrollment.email, enrollment.password))
        return accounts to NativeBooksRepository(accounts)
    }
    private fun contract(kind: NativeInterestPayoutPlanner.Kind, principal: Long,
        bps: Int, end: String) = NativeInterestPayoutPlanner.Input(
            kind, principal, bps, LocalDate.parse("2025-01-31"),
            LocalDate.parse(end))

    @Test fun reviewedSgbCouponsCommitOnceWithoutModifyingRecordedPosition() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(enrollment.email).people.single().id
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic sovereign gold", "GOLD_SGB", "Sovereign Gold Bond",
            100_000_000L, 400_000L, "2025-01-31", true, couponRateBps = 250))
        val repo = NativeInterestPayoutRepository(accounts)
        val review = repo.inspect(enrollment.email, holding,
            contract(NativeInterestPayoutPlanner.Kind.SGB, 400_000L, 250, "2026-01-31"))
        assertEquals(listOf(5_000L, 5_000L),
            review.coupons.map { it.amountMinor })
        assertThrows(IllegalArgumentException::class.java) {
            repo.approve(enrollment.email, review, false)
        }
        assertEquals(1, books.history(enrollment.email, holding).size)
        assertEquals(2, repo.approve(enrollment.email, review, true).posted)
        val ledger = books.history(enrollment.email, holding)
        assertEquals(3, ledger.size)
        assertEquals(2, ledger.count { it.kind == "INTEREST" })
        assertEquals(100_000_000L,
            books.read(enrollment.email).holdings.single().quantityScaled)
        assertEquals(400_000L,
            books.read(enrollment.email).holdings.single().marketPriceMinor)
        assertThrows(IllegalArgumentException::class.java) {
            repo.approve(enrollment.email, review, true)
        }
        assertEquals(3, books.history(enrollment.email, holding).size)
        assertTrue(repo.inspect(enrollment.email, holding,
            contract(NativeInterestPayoutPlanner.Kind.SGB, 400_000L,
                250, "2026-01-31")).coupons.isEmpty())

        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(enrollment.email, enrollment.password))
        assertEquals(3, NativeBooksRepository(reopened)
            .history(enrollment.email, holding).size)
    }

    @Test fun existingManualReceiptAndWrongRecordedRateAreRejected() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(enrollment.email).people.single().id
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic SCSS", "FIXED_INCOME", "SCSS",
            100_000_000L, 100_000L, "2025-01-31", true, interestRateBps = 800))
        val repo = NativeInterestPayoutRepository(accounts)
        val right = contract(NativeInterestPayoutPlanner.Kind.SCSS, 100_000L,
            800, "2025-07-31")
        assertThrows(IllegalArgumentException::class.java) {
            repo.inspect(enrollment.email, holding, right.copy(annualRateBps = 825))
        }
        val review = repo.inspect(enrollment.email, holding, right)
        books.appendUserEvent(enrollment.email, NativeBooksRepository.NewEvent(
            holding, "INTEREST", "2025-04-30", 0L, 0L, 2_000L))
        assertThrows(IllegalArgumentException::class.java) {
            repo.approve(enrollment.email, review, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.inspect(enrollment.email, holding, right)
        }
        assertEquals(0, books.history(enrollment.email, holding)
            .count { it.notes?.contains("owner-confirmed") == true })
    }

    @Test fun financialHistoryChangeWithoutUnitChangeInvalidatesPreview() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(enrollment.email).people.single().id
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic post office", "FIXED_INCOME", "POMIS",
            100_000_000L, 100_000L, "2025-01-31", true, interestRateBps = 1200))
        val repo = NativeInterestPayoutRepository(accounts)
        val review = repo.inspect(enrollment.email, holding,
            contract(NativeInterestPayoutPlanner.Kind.POMIS, 100_000L,
                1200, "2025-03-31"))
        books.appendUserEvent(enrollment.email, NativeBooksRepository.NewEvent(
            holding, "DIVIDEND", "2025-02-15", 0L, 0L, 300L))
        assertThrows(IllegalArgumentException::class.java) {
            repo.approve(enrollment.email, review, true)
        }
        assertEquals(2, books.history(enrollment.email, holding).size)
        assertEquals(0, books.history(enrollment.email, holding)
            .count { it.kind == "INTEREST" })
    }
}
