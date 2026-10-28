package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeAssetTaxonomy

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Fictional owners/holdings. Real encrypted SQLCipher specialist read-edit-reopen acceptance. */
@RunWith(AndroidJUnit4::class)
class NativeM27SpecialistMetadataInstrumentedTest {
    private val credentials = NativeCredentials.Enrollment("owner@example.org",
        "Owner", "Household", "Strong!Pass123", "school", "First School", "pet", "First Pet")
    private fun context(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "m27-specialist-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
        }
    }
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart(); check(it.begin() && it.complete(true))
    }
    private fun open(ctx: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val account = NativeAccountRepository(ctx, gate())
        account.enrollVerified(credentials,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(account.login(credentials.email, credentials.password))
        return account to NativeBooksRepository(account)
    }

    @Test fun typedCategoryFieldsDoNotLeakIntoOtherAssetTypes() {
        assertTrue(NativeHoldingSpecialist.fields("MUTUAL_FUND", "Equity fund")
            .any { it.key == "mfPlan" })
        assertFalse(NativeHoldingSpecialist.fields("GOLD_SGB", "Physical gold (24K)")
            .any { it.key == "mfPlan" })
        assertTrue(NativeHoldingSpecialist.fields("FIXED_INCOME", "NPS")
            .any { it.key == "npsTier" })
        assertFalse(NativeHoldingSpecialist.fields("FIXED_INCOME", "PPF")
            .any { it.key == "npsTier" })
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingSpecialist.merge(null, "EQUITY", "Other",
                mapOf("mfPlan" to "DIRECT"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingSpecialist.merge(null, "MUTUAL_FUND", "Other",
                mapOf("mfPlan" to "INVENTED"))
        }
    }

    @Test fun typedMfMetadataAndUnknownHistoricalKeysSurviveEditAndEncryptedReopen() {
        val ctx = context()
        val (accounts, books) = open(ctx)
        val owner = books.read(credentials.email).people.single().id
        val seeded = NativeHoldingSpecialist.merge("""{"historicalExtra":"preserved"}""",
            "MUTUAL_FUND", "Equity fund",
            mapOf("mfPlan" to "DIRECT", "mfOption" to "GROWTH", "mfHeldAs" to "FOLIO"))
        val holding = books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            owner, null, "Fictional MF", "MUTUAL_FUND", "Equity fund",
            0L, 10_000L, "2024-01-01", false, detailsJson = seeded))
        assertEquals("DIRECT", NativeHoldingSpecialist.read(
            books.read(credentials.email).holdings.single().detailsJson,
            "MUTUAL_FUND", "Equity fund")["mfPlan"])
        val changed = NativeHoldingSpecialist.merge(
            books.read(credentials.email).holdings.single().detailsJson,
            "MUTUAL_FUND", "Equity fund", mapOf("mfPlan" to "REGULAR"))
        books.updateHoldingMetadata(credentials.email, holding, null, null, changed, null)
        assertEquals("preserved", JSONObject(books.read(credentials.email)
            .holdings.single().detailsJson!!).getString("historicalExtra"))
        accounts.onBackground()
        val unlocked = NativeAccountRepository(ctx, gate())
        assertTrue(unlocked.login(credentials.email, credentials.password))
        val facts = NativeBooksRepository(unlocked).read(credentials.email).holdings.single()
        assertEquals("REGULAR", NativeHoldingSpecialist.read(
            facts.detailsJson, "MUTUAL_FUND", "Equity fund")["mfPlan"])
        assertEquals("GROWTH", JSONObject(facts.detailsJson!!).getString("mfOption"))
    }

    @Test fun linkedSpecialistHoldingCannotBeAssignedAcrossOwnersOrToSelf() {
        val (_, books) = open(context())
        val primary = books.read(credentials.email).people.single().id
        val second = books.createPerson(credentials.email,
            NativeBooksRepository.NewPerson("Fictional Second", "Spouse"))
        val foreign = books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            second, null, "Other property", "REAL_ESTATE", "Residential",
            0L, 0L, "2024-01-01", false))
        val own = books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            primary, null, "Own loan", "LIABILITY", "Home loan",
            0L, 0L, "2024-01-01", false))
        val foreignLink = NativeHoldingSpecialist.merge(null, "LIABILITY", "Home loan",
            mapOf("linkedHoldingId" to foreign))
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(credentials.email, own, null, null, foreignLink, null)
        }
        val selfLink = NativeHoldingSpecialist.merge(null, "LIABILITY", "Home loan",
            mapOf("linkedHoldingId" to own))
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(credentials.email, own, null, null, selfLink, null)
        }
        assertNull(books.read(credentials.email).holdings.single { it.id == own }.detailsJson)
        assertThrows(IllegalArgumentException::class.java) {
            books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
                primary, null, "Linked loan", "LIABILITY", "Home loan",
                0L, 0L, "2024-01-01", false, detailsJson = foreignLink))
        }
        assertEquals(2, books.read(credentials.email).holdings.size)
    }

    @Test fun originalMonthlyIncomeDatesClampHistoricalDayAndRespectStartEndAndLeapYear() {
        // Golden source: frozen src/lib/interest.ts monthlyIncomeDates + clampIncomeDay.
        assertEquals(listOf("2024-02-28", "2024-03-28"),
            NativeHoldingSpecialist.monthlyIncomeDates(
                "2024-01-30", 31, "2024-04-30", "2024-03-28"))
        assertEquals(listOf("2024-02-01", "2024-03-01"),
            NativeHoldingSpecialist.monthlyIncomeDates(
                "2024-01-31", 0, "2024-03-01"))
        assertEquals(emptyList<String>(), NativeHoldingSpecialist.monthlyIncomeDates(
            "2024-06-01", 15, "2024-05-31"))
        // Schedule is explanatory only; there are no fabricated actual receipts.
        val details = NativeHoldingSpecialist.merge(null, "REAL_ESTATE", "REIT",
            mapOf("monthlyIncomeMinor" to "12.50", "incomeDay" to "28",
                "incomeStart" to "2024-01-30", "incomeEnd" to "2024-04-28"))
        val projected = NativeHoldingSpecialist.monthlyIncomeSchedule(
            details, "2024-01-01", "2024-03-28")!!
        assertEquals(125_000L, projected.amountMinor)
        assertEquals(2, projected.scheduledThroughAsOf)
        assertEquals(250_000L, projected.scheduledTotalMinor)
        assertEquals("2024-03-28", projected.nextDate)
    }

    @Test fun frozenSchemeDefaultsAndInvalidScheduleFailClosedWithoutSilentClamping() {
        assertEquals("CUMULATIVE",
            NativeHoldingSpecialist.defaults("FIXED_INCOME", "Bank FD")["compounding"])
        assertEquals("I", NativeHoldingSpecialist.defaults("FIXED_INCOME", "NPS")["npsTier"])
        assertEquals("24K",
            NativeHoldingSpecialist.defaults("GOLD_SGB", "Physical gold (24K)")["goldPurity"])
        assertEquals("DEMAT",
            NativeHoldingSpecialist.defaults("REAL_ESTATE", "REIT")["altHoldMode"])
        assertTrue(NativeHoldingSpecialist.fields("SPECIALISED", "AIF")
            .any { it.key == "monthlyIncomeMinor" })
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingSpecialist.merge(null, "SPECIALISED", "AIF",
                mapOf("incomeDay" to "31"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingSpecialist.merge(null, "REAL_ESTATE", "REIT",
                mapOf("incomeStart" to "2025-02-10", "incomeEnd" to "2025-02-09"))
        }
        val full = NativeHoldingSpecialist.merge(
            """{"historicalSpecialist":"keep"}""", "REAL_ESTATE", "REIT",
            mapOf("ownership" to "JOINT", "ownershipPercent" to "37.5",
                "areaSqFt" to "1250", "incomeDay" to "28"))
        val decoded = NativeHoldingSpecialist.read(full, "REAL_ESTATE", "REIT")
        assertEquals("37.5", decoded["ownershipPercent"])
        assertEquals("28", decoded["incomeDay"])
        assertEquals("keep", JSONObject(full!!).getString("historicalSpecialist"))
    }

    @Test fun aifCommitmentAndMonthlySchedulePersistWithoutCreatingIncomeEventsAfterRestart() {
        val ctx = context()
        val (accounts, books) = open(ctx)
        val member = books.read(credentials.email).people.single().id
        val first = NativeHoldingSpecialist.merge(
            """{"legacyOptional":"retain"}""", "SPECIALISED", "AIF",
            mapOf("aifCategory" to "II", "commitmentMinor" to "250000.10",
                "capitalCalledMinor" to "125000.05", "altHoldMode" to "LUMPSUM",
                "monthlyIncomeMinor" to "125.50", "incomeDay" to "28",
                "incomeStart" to "2024-01-30", "incomeEnd" to "2024-03-28"))
        val holding = books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            member, null, "Fictional AIF", "SPECIALISED", "AIF",
            0L, 0L, "2024-01-01", false, detailsJson = first))
        assertTrue(books.history(credentials.email, holding).isEmpty())
        val edited = NativeHoldingSpecialist.merge(
            books.read(credentials.email).holdings.single().detailsJson, "SPECIALISED", "AIF",
            mapOf("monthlyIncomeMinor" to "150.75"))
        books.updateHoldingMetadata(credentials.email, holding, null, null, edited, null)
        val schedule = NativeHoldingSpecialist.monthlyIncomeSchedule(
            books.read(credentials.email).holdings.single().detailsJson,
            "2024-01-01", "2024-03-31")!!
        assertEquals(1_507_500L, schedule.amountMinor)
        assertEquals(2, schedule.scheduledThroughAsOf)
        assertEquals(3_015_000L, schedule.scheduledTotalMinor)
        assertNull(schedule.nextDate)
        assertThrows(IllegalArgumentException::class.java) {
            NativeHoldingSpecialist.merge(edited, "SPECIALISED", "AIF",
                mapOf("incomeDay" to "29"))
        }
        assertEquals(0, books.history(credentials.email, holding).size)
        accounts.onBackground()
        val unlocked = NativeAccountRepository(ctx, gate())
        assertTrue(unlocked.login(credentials.email, credentials.password))
        val reopened = NativeBooksRepository(unlocked).read(credentials.email).holdings.single()
        val obj = JSONObject(reopened.detailsJson!!)
        assertEquals("2500001000", obj.getString("commitmentMinor"))
        assertEquals("1250000500", obj.getString("capitalCalledMinor"))
        assertEquals("retain", obj.getString("legacyOptional"))
        assertEquals("1507500", obj.getString("monthlyIncomeMinor"))
        assertEquals(0, NativeBooksRepository(unlocked).history(credentials.email, holding).size)
    }

    @Test fun allEightZeroPositionCategoriesSurviveEncryptedRestartWithoutInventedCost() {
        val ctx = context()
        val (accounts, books) = open(ctx)
        val owner = books.read(credentials.email).people.single().id
        val categories = listOf("EQUITY", "MUTUAL_FUND", "GOLD_SGB", "FIXED_INCOME",
            "REAL_ESTATE", "SPECIALISED", "CASH_BANK", "LIABILITY")
        categories.forEach { category ->
            val sub = NativeAssetTaxonomy.subcategories.getValue(category).first()
            val name = "Synthetic " + category
            val details = NativeHoldingSpecialist.merge(null, category, sub,
                NativeHoldingSpecialist.defaults(category, sub))
            books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
                owner, null, name, category, sub, 0L, 0L, "2024-01-01",
                false, detailsJson = details))
        }
        val before = books.read(credentials.email).holdings
        assertEquals(8, before.size)
        assertEquals(categories.toSet(), before.map { it.category }.toSet())
        accounts.onBackground()
        val unlocked = NativeAccountRepository(ctx, gate())
        assertTrue(unlocked.login(credentials.email, credentials.password))
        val reopened = NativeBooksRepository(unlocked)
            .read(credentials.email).holdings
        assertEquals(8, reopened.size)
        categories.forEach { category ->
            val original = before.single { it.category == category }
            val restored = reopened.single { it.category == category }
            assertEquals(original.id, restored.id)
            assertEquals(owner, restored.personId)
            assertEquals(0L, restored.quantityScaled)
            assertEquals(original.detailsJson, restored.detailsJson)
        }
    }
}
