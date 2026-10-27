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
}
