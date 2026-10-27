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

/** P13/P14 source provenance + exact owner FIFO on an actual admitted SQLCipher vault. */
@RunWith(AndroidJUnit4::class)
class NativeM27FinancialIntegrityInstrumentedTest {
    private val input = NativeCredentials.Enrollment("owner@example.org", "Fictional",
        "Owner", "Strong!Pass123", "school", "First School", "pet", "First Pet")

    private fun context(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m27-fifo-" + UUID.randomUUID()).apply { mkdirs() }
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
        val account = NativeAccountRepository(ctx, gate())
        account.enrollVerified(input,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(account.login(input.email, input.password))
        return account to NativeBooksRepository(account)
    }

    @Test fun historicalUnmarkedRegistrarQuoteNeverBecomesAcquisitionCostAfterSaleOrRestart() {
        val ctx = context()
        val (account, books) = open(ctx)
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Fictional legacy statement", "EQUITY", "Other", 0L,
            27_500L, "2024-01-01", true))
        val snapshot = UUID.randomUUID().toString()
        // A strictly test-only legacy-compatible row, not customer data or a
        // replacement for the actual reviewed positive CAS acceptance.
        account.withUnlockedVault(input.email) { db ->
            db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
                "holding_name,event_type,trade_date,quantity_scaled,unit_price_minor," +
                "total_amount_minor,charges_minor,source_ref,notes,entered_at,entered_by) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                snapshot, owner, holding, "Fictional legacy statement",
                "SNAPSHOT_ADJUSTMENT", "2024-03-31", 200_000_000L, 27_500L,
                55_000L, 0L, "fictional-source", "Legacy quote only",
                "2026-10-27T00:00:00Z", "statement-import"))
        }
        val initial = books.read(input.email).holdings.single()
        assertEquals(200_000_000L, initial.quantityScaled)
        assertTrue(initial.unknownCost)
        assertEquals(0L, initial.knownCostMinor)
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                holding, "CORRECTION", "2024-03-31", 200_000_000L, 27_500L,
                55_000L, supersedes = snapshot))
        }
        val sale = books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2024-04-01", 50_000_000L, 30_000L,
            15_000L, 13L))
        val after = books.read(input.email).holdings.single()
        assertTrue(after.unknownCost)
        assertEquals(150_000_000L, after.quantityScaled)
        assertEquals(0L, after.knownCostMinor)
        assertEquals(listOf(snapshot, sale).toSet(),
            books.history(input.email, holding).map { it.id }.toSet())
        account.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        val persisted = NativeBooksRepository(reopened)
        assertTrue(persisted.read(input.email).holdings.single().unknownCost)
        assertEquals(0L, persisted.read(input.email).holdings.single().knownCostMinor)
        assertEquals(listOf(snapshot, sale).toSet(),
            persisted.history(input.email, holding).map { it.id }.toSet())
    }

    @Test fun sameDaySplitSaleChargesAndForeignCorrectionAreAtomicAndOwnerScopedAfterRestart() {
        val ctx = context()
        val (account, books) = open(ctx)
        val primary = books.read(input.email).people.single().id
        val other = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Fictional other", "Spouse"))
        val a = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Primary fictional equity", "EQUITY", "Other",
            0L, 30_000L, "2025-01-15", true))
        val b = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            other, null, "Other fictional equity", "EQUITY", "Other",
            100_000_000L, 50_000L, "2025-01-15", true))
        val foreignOpening = books.history(input.email, b).single().id
        fun append(kind: String, quantity: Long, unit: Long, amount: Long,
            fee: Long = 0L) = books.appendUserEvent(input.email,
            NativeBooksRepository.NewEvent(a, kind, "2025-01-15",
                quantity, unit, amount, fee))
        val firstBuyId = append("BUY", 100_000_000L, 30_000L, 30_000L, 100L)
        val secondBuyId = append("BUY", 200_000_000L, 40_000L, 80_000L, 200L)
        // Frozen src/lib/lots.ts orders same-day BUY parcels by immutable event
        // ID, NOT by insert call order. The two UUIDs can sort either way.
        // Both outcomes have exact, independent FIFO cost vectors.
        val expectedCost = if (firstBuyId < secondBuyId) 53_467L else 43_467L
        append("SPLIT", 3L, 2L, 0L)
        append("SELL", 250_000_000L, 60_000L, 150_000L, 101L)
        val before = books.history(input.email, a).map { it.id }
        val ownerBooks = books.read(input.email).holdings.associateBy { it.id }
        assertEquals(200_000_000L, ownerBooks.getValue(a).quantityScaled)
        assertEquals(expectedCost, ownerBooks.getValue(a).knownCostMinor)
        assertEquals(100_000_000L, ownerBooks.getValue(b).quantityScaled)
        assertEquals(50_000L, ownerBooks.getValue(b).knownCostMinor)
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                a, "CORRECTION", "2025-01-15", 200_000_000L,
                30_000L, 60_000L, supersedes = foreignOpening))
        }
        assertThrows(IllegalArgumentException::class.java) {
            append("SELL", 500_000_000L, 60_000L, 300_000L, 200L)
        }
        assertEquals(before, books.history(input.email, a).map { it.id })
        account.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        val data = NativeBooksRepository(reopened)
        val persisted = data.read(input.email).holdings.associateBy { it.id }
        assertEquals(200_000_000L, persisted.getValue(a).quantityScaled)
        assertEquals(expectedCost, persisted.getValue(a).knownCostMinor)
        assertEquals(100_000_000L, persisted.getValue(b).quantityScaled)
        assertEquals(before, data.history(input.email, a).map { it.id })
        assertEquals(listOf(foreignOpening), data.history(input.email, b).map { it.id })
    }

    @Test fun invalidManualCashAndBonusCannotPostPartialTradesAcrossEncryptedRestart() {
        val ctx = context()
        val (account, books) = open(ctx)
        val primary = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Fictional cash review", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true))
        val before = books.history(input.email, holding).map { it.id }
        val invalid = listOf(
            NativeBooksRepository.NewEvent(holding, "BUY", "2025-01-01",
                100_000_000L, 10_000L, -10_000L),
            NativeBooksRepository.NewEvent(holding, "SELL", "2025-01-01",
                50_000_000L, 11_000L, -5_500L),
            NativeBooksRepository.NewEvent(holding, "BONUS", "2025-01-01",
                25_000_000L, 0L, 100L),
            NativeBooksRepository.NewEvent(holding, "BONUS", "2025-01-01",
                25_000_000L, 0L, 0L, 50L))
        for (form in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                books.appendUserEvent(input.email, form)
            }
            assertEquals(before, books.history(input.email, holding).map { it.id })
        }
        val snapshot = books.read(input.email).holdings.single()
        assertEquals(100_000_000L, snapshot.quantityScaled)
        assertEquals(10_000L, snapshot.knownCostMinor)
        account.onBackground()
        val again = NativeAccountRepository(ctx, gate())
        assertTrue(again.login(input.email, input.password))
        val persisted = NativeBooksRepository(again)
        assertEquals(before, persisted.history(input.email, holding).map { it.id })
        assertEquals(10_000L,
            persisted.read(input.email).holdings.single().knownCostMinor)
    }
}
