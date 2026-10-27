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

/** M27: synthetic SQLCipher custody provenance, no private CAS or customer accounts. */
@RunWith(AndroidJUnit4::class)
class NativeM27CustodyProvenanceInstrumentedTest {
    private val enrollment = NativeCredentials.Enrollment(
        "owner@example.org", "Owner", "Household", "Strong!Pass123",
        "school", "First School", "pet", "First Pet")

    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m27-custody-" + UUID.randomUUID()).apply { mkdirs() }
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

    private fun enroll(context: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(context, gate())
        accounts.enrollVerified(enrollment,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(accounts.login(enrollment.email, enrollment.password))
        return accounts to NativeBooksRepository(accounts)
    }

    private fun custody(books: NativeBooksRepository, owner: String, label: String): String =
        books.createCustody(enrollment.email, NativeBooksRepository.NewCustody(
            owner, "DEMAT", label))

    @Test fun immutableTradeCustodyCannotBeRelabeledEvenAfterEncryptedRestart() {
        val context = sandbox()
        val (accounts, books) = enroll(context)
        val owner = books.read(enrollment.email).people.single().id
        val original = custody(books, owner, "Synthetic original")
        val other = custody(books, owner, "Synthetic other")
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, original, "Synthetic equity", "EQUITY", "Large cap",
            100_000_000L, 10_000L, "2024-01-01", true))
        for (target in listOf(other, null)) {
            assertThrows(IllegalArgumentException::class.java) {
                books.updateHoldingMetadata(enrollment.email, holding, target,
                    "Should roll back", null, null)
            }
        }
        assertEquals(original, books.read(enrollment.email).holdings.single().accountId)
        assertNull(books.read(enrollment.email).holdings.single().notes)
        assertEquals(1, books.history(enrollment.email, holding).size)
        accounts.withUnlockedVault(enrollment.email) { db ->
            db.rawQuery("SELECT account_id FROM ledger_event WHERE holding_id=?",
                arrayOf(holding)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(original, c.getString(0))
            }
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(enrollment.email, enrollment.password))
        assertEquals(original, NativeBooksRepository(reopened)
            .read(enrollment.email).holdings.single().accountId)
    }

    @Test fun zeroQuantityStatementPositionAlsoPreventsAccountRelabel() {
        val (accounts, books) = enroll(sandbox())
        val owner = books.read(enrollment.email).people.single().id
        val original = custody(books, owner, "Synthetic original")
        val other = custody(books, owner, "Synthetic other")
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, original, "Empty statement position", "EQUITY", "Large cap",
            0L, 0L, "2024-01-01", false))
        assertTrue(books.history(enrollment.email, holding).isEmpty())
        val batch = UUID.randomUUID().toString()
        accounts.withUnlockedVault(enrollment.email) { db ->
            db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename,document_kind," +
                "committed_at) VALUES(?,?,?,?)", arrayOf<Any>(
                batch, "synthetic-only.pdf", "CAS", "2026-10-27T00:00:00Z"))
            db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
                "statement_date,quantity_scaled,unit_price_minor) VALUES(?,?,?,?,?)",
                arrayOf<Any>(batch, holding, "2024-01-01", 0L, 0L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(enrollment.email, holding, other, null, null, null)
        }
        assertEquals(original, books.read(enrollment.email).holdings.single().accountId)
        accounts.withUnlockedVault(enrollment.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM statement_position WHERE holding_id=?",
                arrayOf(holding)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(0))
            }
        }
    }

    @Test fun trulyUnrecordedHoldingMayChooseAccountBeforeFirstEvent() {
        val (accounts, books) = enroll(sandbox())
        val owner = books.read(enrollment.email).people.single().id
        val original = custody(books, owner, "Synthetic original")
        val other = custody(books, owner, "Synthetic other")
        val holding = books.createHolding(enrollment.email, NativeBooksRepository.NewHolding(
            owner, original, "Never recorded", "EQUITY", "Large cap",
            0L, 0L, "2024-01-01", false))
        books.updateHoldingMetadata(enrollment.email, holding, other,
            "Allowed before first event", null, null)
        assertEquals(other, books.read(enrollment.email).holdings.single().accountId)
        books.appendUserEvent(enrollment.email, NativeBooksRepository.NewEvent(
            holding, "BUY", "2024-02-01", 100_000_000L, 10_000L, 10_000L))
        accounts.withUnlockedVault(enrollment.email) { db ->
            db.rawQuery("SELECT account_id FROM ledger_event WHERE holding_id=?",
                arrayOf(holding)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(other, c.getString(0))
            }
        }
    }
}
