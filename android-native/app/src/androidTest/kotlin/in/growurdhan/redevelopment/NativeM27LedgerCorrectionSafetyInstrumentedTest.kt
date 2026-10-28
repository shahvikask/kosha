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

/** Isolated SQLCipher regression. All owners, prices and statements are fictional. */
@RunWith(AndroidJUnit4::class)
class NativeM27LedgerCorrectionSafetyInstrumentedTest {
    private val credential = NativeCredentials.Enrollment(
        "owner@example.org", "Owner", "Family", "Strong!Pass123",
        "school", "First School", "pet", "First Pet")

    private fun context(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m27-event-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
        }
    }
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun open(ctx: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(ctx, gate())
        accounts.enrollVerified(credential,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(accounts.login(credential.email, credential.password))
        return accounts to NativeBooksRepository(accounts)
    }
    private fun holding(books: NativeBooksRepository, qty: Long = 0L): String {
        val owner = books.read(credential.email).people.single().id
        return books.createHolding(credential.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic equity", "EQUITY", "Other", qty, 10_000L,
            "2024-01-01", true))
    }

    @Test fun importedSnapshotAndSourcedPurchaseCannotBeReplacedByManualCorrection() {
        val (accounts, books) = open(context())
        val h = holding(books)
        val owner = books.read(credential.email).people.single().id
        val sourceIds = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        accounts.withUnlockedVault(credential.email) { db ->
            listOf("SNAPSHOT_ADJUSTMENT", "BUY").forEachIndexed { n, kind ->
                db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
                    "holding_name,event_type,trade_date,quantity_scaled,unit_price_minor," +
                    "total_amount_minor,charges_minor,entered_at,entered_by,source_ref) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                    sourceIds[n], owner, h, "Synthetic equity", kind, "2024-02-01",
                    if (kind == "BUY") 100_000_000L else 0L,
                    10_000L, 10_000L, 0L, "2026-10-27T00:00:00Z",
                    "registrar", "fictional-import"))
            }
        }
        val sourceHistory = books.history(credential.email, h)
        assertEquals(2, sourceHistory.size)
        assertTrue(sourceHistory.all { it.sourceBacked })
        assertTrue(sourceHistory.any { it.kind == "SNAPSHOT_ADJUSTMENT" })
        for (source in sourceIds) {
            assertThrows(IllegalArgumentException::class.java) {
                books.appendUserEvent(credential.email, NativeBooksRepository.NewEvent(
                    h, "CORRECTION", "2024-02-02", 200_000_000L, 10_000L,
                    20_000L, supersedes = source))
            }
        }
        assertEquals(2, books.history(credential.email, h).size)
        assertEquals(100_000_000L, books.read(credential.email).holdings.single().quantityScaled)
    }

    @Test fun validOwnerCorrectionChainAndSourceRemainAppendOnlyAcrossEncryptedRestart() {
        val ctx = context()
        val (accounts, books) = open(ctx)
        val h = holding(books, 100_000_000L)
        val original = books.history(credential.email, h).single().id
        val first = books.appendUserEvent(credential.email, NativeBooksRepository.NewEvent(
            h, "CORRECTION", "2024-02-01", 200_000_000L, 10_000L,
            20_000L, supersedes = original))
        books.appendUserEvent(credential.email, NativeBooksRepository.NewEvent(
            h, "CORRECTION", "2024-02-02", 300_000_000L, 10_000L,
            30_000L, supersedes = first))
        assertEquals(3, books.history(credential.email, h).size)
        assertEquals(300_000_000L, books.read(credential.email).holdings.single().quantityScaled)
        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(credential.email, credential.password))
        val other = NativeBooksRepository(reopened)
        assertEquals(3, other.history(credential.email, h).size)
        assertEquals(300_000_000L, other.read(credential.email).holdings.single().quantityScaled)
    }

    @Test fun eventKindRulesRollbackAndDatedValuationStaySeparatedFromLots() {
        val (accounts, books) = open(context())
        val h = holding(books, 100_000_000L)
        val initial = books.history(credential.email, h).size
        val invalid = listOf(
            NativeBooksRepository.NewEvent(h, "DIVIDEND", "2024-06-01", 1L, 0L, 500L),
            NativeBooksRepository.NewEvent(h, "INTEREST", "2024-06-01", 0L, 50L, 500L),
            NativeBooksRepository.NewEvent(h, "SPLIT", "2024-06-01", 2L, 1L, 500L),
            NativeBooksRepository.NewEvent(h, "VALUATION_UPDATE", "2024-06-01", 1L, 30_000L, 0L),
            NativeBooksRepository.NewEvent(h, "REPAYMENT", "2024-06-01", 0L, 500L, 500L))
        for (form in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                books.appendUserEvent(credential.email, form)
            }
        }
        assertEquals(initial, books.history(credential.email, h).size)
        val valueEvent = books.appendUserEvent(credential.email,
            NativeBooksRepository.NewEvent(h, "VALUATION_UPDATE",
                "2025-01-02", 0L, 27_000L, 0L))
        assertEquals(100_000_000L, books.read(credential.email).holdings.single().quantityScaled)
        assertEquals(27_000L, books.read(credential.email).holdings.single().marketPriceMinor)
        assertEquals("USER_OVERRIDE", books.read(credential.email).holdings.single().priceSource)
        assertTrue(books.history(credential.email, h).any { it.id == valueEvent })
        accounts.withUnlockedVault(credential.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM price_observation WHERE source='USER_OVERRIDE'",
                null).use { c -> assertTrue(c.moveToFirst()); assertEquals(1L, c.getLong(0)) }
        }
    }
}
