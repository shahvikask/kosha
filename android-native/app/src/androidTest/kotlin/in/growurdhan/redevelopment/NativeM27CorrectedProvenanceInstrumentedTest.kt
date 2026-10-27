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

/** Fictional owners and prices only. SQLCipher source-provenance and atomic rollback proof. */
@RunWith(AndroidJUnit4::class)
class NativeM27CorrectedProvenanceInstrumentedTest {
    private val owner = NativeCredentials.Enrollment(
        "owner@example.org", "Fictional", "Owner", "Strong!Pass123",
        "school", "First School", "pet", "First Pet")

    private fun sandbox(): Context {
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(root.cacheDir, "m27-correction-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(root) {
            override fun getDatabasePath(name: String) =
                File(dir, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() = File(dir, "no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(dir, "files").also { it.mkdirs() }
        }
    }

    private fun admission() = NativeDeviceAdmission().also {
        it.onStart(); check(it.begin() && it.complete(true))
    }
    private fun create(ctx: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(ctx, admission())
        accounts.enrollVerified(owner,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(accounts.login(owner.email, owner.password))
        return accounts to NativeBooksRepository(accounts)
    }
    private fun holding(books: NativeBooksRepository, known: Boolean = true,
        unitPrice: Long = 10_000L): String {
        val member = books.read(owner.email).people.single().id
        return books.createHolding(owner.email, NativeBooksRepository.NewHolding(
            member, null, "Fictional equity", "EQUITY", "Other",
            100_000_000L, unitPrice, "2024-01-01", known))
    }

    @Test fun unknownCostOpeningCorrectionCannotFabricateBasisAndSurvivesEncryptedReopen() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val id = holding(books, known = false, unitPrice = 45_000L)
        val openingId = books.history(owner.email, id).single().id
        assertTrue(books.read(owner.email).holdings.single().unknownCost)
        // This must rollback without leaving a corrected lot or claim of price verification.
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(owner.email, NativeBooksRepository.NewEvent(
                id, "CORRECTION", "2024-02-01", 200_000_000L, 45_000L,
                90_000L, supersedes = openingId))
        }
        assertEquals(1, books.history(owner.email, id).size)
        val corrected = books.appendUserEvent(owner.email,
            NativeBooksRepository.NewEvent(id, "CORRECTION",
                "2024-02-01", 200_000_000L, 0L, 0L, supersedes = openingId))
        val inMemory = books.read(owner.email).holdings.single()
        assertTrue(inMemory.unknownCost)
        assertEquals(0L, inMemory.knownCostMinor)
        assertEquals(200_000_000L, inMemory.quantityScaled)
        accounts.withUnlockedVault(owner.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf("native.ledger.cost_source.$corrected")).use { cur ->
                assertTrue(cur.moveToFirst())
                assertEquals("UNKNOWN", cur.getString(0))
            }
        }
        accounts.onBackground()
        val again = NativeAccountRepository(ctx, admission())
        assertTrue(again.login(owner.email, owner.password))
        val reopened = NativeBooksRepository(again).read(owner.email).holdings.single()
        assertTrue(reopened.unknownCost)
        assertEquals(0L, reopened.knownCostMinor)
        assertEquals(200_000_000L, reopened.quantityScaled)
    }

    @Test fun verifiedZeroCostOpeningRemainsKnownAcrossCorrectionAndEncryptedReopen() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val id = holding(books, known = true, unitPrice = 0L)
        val start = books.history(owner.email, id).single().id
        val edit = books.appendUserEvent(owner.email, NativeBooksRepository.NewEvent(
            id, "CORRECTION", "2024-02-01", 300_000_000L, 0L, 0L,
            supersedes = start))
        assertFalse(books.read(owner.email).holdings.single().unknownCost)
        accounts.withUnlockedVault(owner.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key=?",
                arrayOf("native.ledger.cost_source.$edit")).use { cur ->
                assertTrue(cur.moveToFirst())
                assertEquals("KNOWN", cur.getString(0))
            }
        }
        accounts.onBackground()
        val again = NativeAccountRepository(ctx, admission())
        assertTrue(again.login(owner.email, owner.password))
        val result = NativeBooksRepository(again).read(owner.email).holdings.single()
        assertFalse(result.unknownCost)
        assertEquals(0L, result.knownCostMinor)
        assertEquals(300_000_000L, result.quantityScaled)
    }

    @Test fun correctedValuationChainPublishesLatestSameDayPriceWithoutChangingLots() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val id = holding(books)
        val original = books.appendUserEvent(owner.email,
            NativeBooksRepository.NewEvent(id, "VALUATION_UPDATE",
                "2025-01-02", 0L, 27_000L, 0L))
        val edit1 = books.appendUserEvent(owner.email,
            NativeBooksRepository.NewEvent(id, "CORRECTION",
                "2025-01-02", 0L, 33_000L, 0L, supersedes = original))
        books.appendUserEvent(owner.email, NativeBooksRepository.NewEvent(
            id, "CORRECTION", "2025-01-02", 0L, 35_000L, 0L,
            supersedes = edit1))
        val snapshot = books.read(owner.email).holdings.single()
        assertEquals(35_000L, snapshot.marketPriceMinor)
        assertEquals("USER_OVERRIDE", snapshot.priceSource)
        assertEquals(100_000_000L, snapshot.quantityScaled)
        assertEquals(10_000L, snapshot.knownCostMinor)
        assertEquals(4, books.history(owner.email, id).size)
        accounts.withUnlockedVault(owner.email) { db ->
            db.rawQuery("SELECT price_minor FROM price_observation WHERE source='USER_OVERRIDE' " +
                "ORDER BY price_date DESC,ingested_at DESC LIMIT 1", null).use { cur ->
                assertTrue(cur.moveToFirst())
                assertEquals(35_000L, cur.getLong(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM price_observation WHERE source='USER_OVERRIDE'",
                null).use { cur ->
                assertTrue(cur.moveToFirst())
                assertEquals(3L, cur.getLong(0))
            }
        }
        accounts.onBackground()
        val again = NativeAccountRepository(ctx, admission())
        assertTrue(again.login(owner.email, owner.password))
        assertEquals(35_000L, NativeBooksRepository(again)
            .read(owner.email).holdings.single().marketPriceMinor)
    }

    @Test fun changingCorrectedValuationDateRollsBackAndOlderPriceCannotRewindCurrent() {
        val (accounts, books) = create(sandbox())
        val id = holding(books)
        val observed = books.appendUserEvent(owner.email,
            NativeBooksRepository.NewEvent(id, "VALUATION_UPDATE",
                "2025-01-02", 0L, 27_000L, 0L))
        books.recordManualPrice(owner.email, id, 40_000L, "2025-02-01")
        val before = books.history(owner.email, id).size
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(owner.email, NativeBooksRepository.NewEvent(
                id, "CORRECTION", "2025-01-03", 0L, 30_000L, 0L,
                supersedes = observed))
        }
        assertEquals(before, books.history(owner.email, id).size)
        books.appendUserEvent(owner.email, NativeBooksRepository.NewEvent(
            id, "CORRECTION", "2025-01-02", 0L, 30_000L, 0L,
            supersedes = observed))
        assertEquals(40_000L, books.read(owner.email).holdings.single().marketPriceMinor)
        accounts.withUnlockedVault(owner.email) { db ->
            db.rawQuery("SELECT price_minor FROM price_observation WHERE price_date<=? " +
                "ORDER BY price_date DESC,ingested_at DESC LIMIT 1",
                arrayOf("2025-01-02")).use { cur ->
                assertTrue(cur.moveToFirst())
                assertEquals(30_000L, cur.getLong(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM price_observation", null).use { cur ->
                assertTrue(cur.moveToFirst()); assertEquals(3L, cur.getLong(0))
            }
        }
    }
}
