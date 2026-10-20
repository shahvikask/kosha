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
}
