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

/** Real encrypted SQLCipher/Keystore records in an isolated Android test sandbox. */
@RunWith(AndroidJUnit4::class)
class NativeBooksRepositoryInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "p01-p34-books-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }
    private val input = NativeCredentials.Enrollment("owner@example.org", "Owner", "Family",
        "Strong!Pass123", "school", "First School", "pet", "First Pet")
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun create(context: Context, admissionGate: NativeDeviceAdmission = gate()):
        Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(context, admissionGate)
        accounts.enrollVerified(input,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(input.email, input.password))
        return accounts to NativeBooksRepository(accounts)
    }

    @Test fun genuineEncryptedRegistrationStoresCurrentVersionedTermsAndPrimaryOwner() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val snapshot = books.read(input.email)
        assertEquals(1, snapshot.people.size)
        assertEquals("Self", snapshot.people.single().relation)
        assertTrue(snapshot.people.single().isPrimary)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.terms.text_id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(NativeRegistrationProof.TERMS_TEXT_ID, it.getString(0))
            }
            db.rawQuery("SELECT pan_masked,date_of_birth FROM vault_account", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("ABCDE****F", it.getString(0))
                assertEquals("1990-06-12", it.getString(1))
            }
        }
    }

    @Test fun familyCustodyHoldingLedgerAndPriceSurviveEncryptedReopen() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val member = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Family Member", "Spouse", panForStatements = "PQRST1234F"))
        val custody = books.createCustody(input.email, NativeBooksRepository.NewCustody(
            member, "DEMAT", "Depository", identifierMasked = "****1234"))
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            member, custody, "Example Equity", "EQUITY", "Large cap",
            100_000_000L, 150_000L, "2024-01-01", true))
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2025-02-01", 40_000_000L, 200_000L, 80_000L, 0L))
        books.recordManualPrice(input.email, holding, 220_000L, "2025-02-02")
        val current = books.read(input.email).holdings.single()
        assertEquals(60_000_000L, current.quantityScaled)
        assertEquals(90_000L, current.knownCostMinor)
        assertEquals("USER_OVERRIDE", current.priceSource)
        assertEquals(2, books.history(input.email, holding).size)
        accounts.onBackground()
        val restarted = NativeAccountRepository(context, gate())
        assertTrue(restarted.login(input.email, input.password))
        val persisted = NativeBooksRepository(restarted).read(input.email)
        assertEquals("PQRST****F", persisted.people.single { it.id == member }.maskedPan) // Never output full PAN in summaries.
        assertEquals(60_000_000L, persisted.holdings.single().quantityScaled)
        assertEquals(220_000L, persisted.holdings.single().marketPriceMinor)
    }

    @Test fun wrongOwnerAndPrimaryDeletionFailWithoutChangingEncryptedData() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        try {
            books.archivePerson(input.email, primary)
            fail("Primary member cannot be archived")
        } catch (_: IllegalArgumentException) { }
        val member = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Another Member", "Spouse"))
        val foreign = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Third Member", "Parent"))
        val account = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(member, "FOLIO", "Mutual Fund"))
        try {
            books.createHolding(input.email, NativeBooksRepository.NewHolding(
                foreign, account, "Invalid", "MUTUAL_FUND", "Index / ETF",
                100_000_000L, 10_000L, "2025-01-01", true))
            fail("Cross-owner custody must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, books.read(input.email).holdings.size)
    }

    @Test fun oversellRollsBackAndLedgerTriggerPreventsMutation() {
        val (accounts, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Test Units", "MUTUAL_FUND", "Index / ETF",
            100_000_000L, 10_000L, "2024-01-01", true))
        val original = books.history(input.email, holding).single().id
        try {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                holding, "SELL", "2025-01-01", 200_000_000L, 10_000L, 20_000L))
            fail("Overselling cannot commit")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, books.history(input.email, holding).size)
        try {
            accounts.withUnlockedVault(input.email) { db ->
                db.execSQL("UPDATE ledger_event SET quantity_scaled=0 WHERE ledger_event_id=?",
                    arrayOf<Any>(original))
            }
            fail("Immutability trigger must abort")
        } catch (_: Exception) { }
        assertEquals(100_000_000L, books.read(input.email).holdings.single().quantityScaled)
    }

    @Test fun emptyHoldingAndUnknownOpeningCostAreNotInvented() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Empty", "GOLD_SGB", "Digital gold", 0L, 0L, "2024-01-01", false))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Unknown Cost", "EQUITY", "Other",
            100_000_000L, 30_000L, "2024-01-01", false))
        val holdings = books.read(input.email).holdings
        assertEquals(0L, holdings.single { it.name == "Empty" }.quantityScaled)
        assertTrue(holdings.single { it.name == "Unknown Cost" }.unknownCost)
    }
    @Test fun currentTermsAndOriginalAccountIdentityAreRequiredForAllNewVaultCreation() {
        val context = sandbox()
        val repo = NativeAccountRepository(context, gate())
        assertThrows(IllegalStateException::class.java) { repo.enroll(input) }
        assertFalse(context.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertThrows(IllegalArgumentException::class.java) {
            repo.enrollVerified(input, NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
                NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                    NativeRegistrationProof.TERMS_TEXT_ID,
                    "2026-10-19T10:00:00Z", false))
        }
        assertFalse(context.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

    @Test fun existingEncryptedVaultNeedsPasswordAndFullCurrentTermsBeforeReunlock() {
        val context = sandbox()
        val (repo, books) = create(context)
        repo.withUnlockedVault(input.email) { db ->
            db.execSQL("DELETE FROM setting WHERE key='m25.terms.text_id'")
        }
        repo.lock()
        assertEquals(NativeAccountRepository.SignInResult.TERMS_REQUIRED,
            repo.loginResult(input.email, input.password))
        assertThrows(IllegalStateException::class.java) { books.read(input.email) }
        assertEquals(NativeAccountRepository.SignInResult.DENIED,
            repo.loginResult(input.email, "incorrect", NativeRegistrationProof.Consent(
                NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true)))
        val consent = NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
            NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true)
        assertEquals(NativeAccountRepository.SignInResult.UNLOCKED,
            repo.loginResult(input.email, input.password, consent))
        assertEquals(1, books.read(input.email).people.size)
        repo.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.terms.text_id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(NativeRegistrationProof.TERMS_TEXT_ID, it.getString(0))
            }
        }
    }

    @Test fun identityEditsGoalsAndCustodyClosureNeverReassignExistingBooks() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val member = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Parent Initial", "Parent"))
        books.updatePerson(input.email, member,
            NativeBooksRepository.NewPerson("Parent Updated", "Parent",
                panForStatements = "PQRST1234F"))
        assertEquals("PQRST****F", books.read(input.email).people.single {
            it.id == member }.maskedPan)
        val goal = books.createGoal(input.email, member, "College", 1_000_000L,
            "2032-01-01", "HIGH")
        books.updateGoal(input.email, goal, member, "Education", 2_000_000L,
            "2031-01-01", "HIGH")
        assertEquals(2_000_000L, books.read(input.email).goals.single {
            it.id == goal }.targetMinor)
        val acct = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(member, "DEMAT", "Depository"))
        books.updateCustody(input.email, acct,
            NativeBooksRepository.NewCustody(member, "DEMAT", "New Depository"))
        assertEquals("New Depository", books.read(input.email).custody.single {
            it.id == acct }.custodian)
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            member, acct, "Fund units", "MUTUAL_FUND", "Index / ETF",
            100_000_000L, 10_000L, "2024-01-01", true))
        books.assignGoal(input.email, holding, goal)
        assertThrows(IllegalArgumentException::class.java) {
            books.updatePerson(input.email, member,
                NativeBooksRepository.NewPerson("Changed classification", "Spouse"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateCustody(input.email, acct,
                NativeBooksRepository.NewCustody(member, "BANK", "Changed custody"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.removeUnlinkedGoal(input.email, goal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.closeCustody(input.email, member, acct, "2026-10-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.archivePerson(input.email, member)
        }
        val someoneElsesGoal = books.createGoal(input.email, primary, "Different",
            100_000L, "2027-01-01", "LOW")
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(input.email, holding, someoneElsesGoal)
        }
        assertEquals(member, books.read(input.email).holdings.single().personId)
    }

    @Test fun correctionAndClosingPositionKeepImmutableHistoryAndArchiveOnlyAtZero() {
        val (accounts, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Split-adjusted stock", "EQUITY", "Large cap",
            100_000_000L, 15_000L, "2024-01-01", true))
        val opening = books.history(input.email, holding).single().id
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "CORRECTION", "2024-01-02",
            200_000_000L, 15_000L, 30_000L, supersedes = opening))
        assertEquals(200_000_000L, books.read(input.email).holdings.single().quantityScaled)
        assertThrows(IllegalArgumentException::class.java) {
            books.setHoldingArchived(input.email, holding, true)
        }
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2025-04-01", 200_000_000L, 17_000L, 34_000L))
        books.setHoldingArchived(input.email, holding, true)
        assertTrue(books.read(input.email).holdings.single().archived)
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                holding, "BUY", "2025-04-02", 100_000_000L, 10_000L, 10_000L))
        }
        assertEquals(3, books.history(input.email, holding).size)
        books.setHoldingArchived(input.email, holding, false)
        assertFalse(books.read(input.email).holdings.single().archived)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM ledger_event WHERE holding_id=?",
                arrayOf(holding)).use {
                assertTrue(it.moveToFirst())
                assertEquals(3L, it.getLong(0))
            }
        }
    }

    @Test fun explicitFreeOpeningRemainsKnownZeroCostAcrossEncryptedRestart() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val primary = books.read(input.email).people.single().id
        val id = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Genuine zero cost gift", "EQUITY", "Other",
            100_000_000L, 0L, "2024-01-01", true))
        assertFalse(books.read(input.email).holdings.single().unknownCost)
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val persisted = NativeBooksRepository(reopened).read(input.email).holdings.single()
        assertEquals(id, persisted.id)
        assertFalse(persisted.unknownCost)
        assertEquals(0L, persisted.knownCostMinor)
    }

    @Test fun specialistMetadataAndIndependentAcquisitionCostPersistInSqlcipher() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val primary = books.read(input.email).people.single().id
        val id = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Synthetic RBI bond", "FIXED_INCOME", "Bond / GOI",
            100_000_000L, 50_000L, "2024-01-01", true,
            isin = "INE000000001", detailsJson = """{"compounding":"HALF_YEARLY"}""",
            openingUnitPriceMinor = 40_000L, institutionName = "Government Issuer",
            accountNumberMasked = "****1234", maturityDate = "2030-01-01",
            couponRateBps = 825, interestRateBps = 825, monthlyEmiMinor = 10000L,
            emiStatus = "PAYING", remainingEmiCount = 18, location = "India"))
        assertEquals(40_000L, books.read(input.email).holdings.single().knownCostMinor)
        assertEquals(50_000L, books.read(input.email).holdings.single().marketPriceMinor)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT coupon_rate_bps,interest_rate_bps,monthly_emi_minor," +
                "emi_status,remaining_emi_count,details_json,institution_name,maturity_date " +
                "FROM holding WHERE holding_id=?", arrayOf(id)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(825, c.getInt(0))
                assertEquals(825, c.getInt(1))
                assertEquals(10000L, c.getLong(2))
                assertEquals("PAYING", c.getString(3))
                assertEquals(18, c.getInt(4))
                assertEquals("""{"compounding":"HALF_YEARLY"}""", c.getString(5))
                assertEquals("Government Issuer", c.getString(6))
                assertEquals("2030-01-01", c.getString(7))
            }
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        assertEquals(40_000L, NativeBooksRepository(reopened).read(input.email)
            .holdings.single().knownCostMinor)
    }

    @Test fun emiCatchUpPostsOnceAndUpdatesCursorAtomicallyAcrossEncryptedReopen() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(input.email).people.single().id
        val loan = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic family loan", "LIABILITY", "Home loan",
            0L, 25_000L, "2025-01-01", knownOpeningCost = false))
        books.updateEmiSchedule(input.email, loan, "PAYING", 10_000L, "2025-01-31", 5)
        val first = books.catchUpEmi(input.email, loan, "2025-02-28")!!
        assertEquals(2, first.payments.size)
        assertEquals(5_000L, first.outstandingMinor)
        assertEquals("2025-03-28", first.nextDueDate)
        assertNull(books.catchUpEmi(input.email, loan, "2025-02-28"))
        assertEquals(2, books.history(input.email, loan).size)
        accounts.onBackground()

        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        val fresh = NativeBooksRepository(reopened)
        val finalPlan = fresh.catchUpEmi(input.email, loan, "2025-03-31")!!
        assertEquals(1, finalPlan.payments.size)
        assertEquals(5_000L, finalPlan.payments.single().amountMinor)
        assertEquals("STOPPED", finalPlan.status)
        assertEquals(3, fresh.history(input.email, loan).size)
        assertNull(fresh.catchUpEmi(input.email, loan, "2025-07-31"))
    }

}
