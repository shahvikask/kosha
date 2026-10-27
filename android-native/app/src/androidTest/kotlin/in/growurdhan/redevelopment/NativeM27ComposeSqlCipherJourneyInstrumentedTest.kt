package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Material3 form confirmation -> real admitted SQLCipher repository -> encrypted restart.
 * Fictitious account and holdings only; no customer statements, credentials or screenshots.
 */
@RunWith(AndroidJUnit4::class)
class NativeM27ComposeSqlCipherJourneyInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val credentials = NativeCredentials.Enrollment(
        "owner@example.org", "Fictional", "Household", "Strong!Pass123",
        "school", "First School", "pet", "First Pet")

    private fun isolate(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "m27-compose-sqlcipher-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(target) {
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
        accounts.enrollVerified(credentials,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(accounts.login(credentials.email, credentials.password))
        return accounts to NativeBooksRepository(accounts)
    }
    private fun <T> io(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    @Test fun reviewedMutualFundUnknownCostWizardWritesEncryptedDetailsAndSurvivesRestart() {
        val ctx = isolate()
        val (accounts, books) = open(ctx)
        val owner = io { books.read(credentials.email).people.single() }
        var savedId: String? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingForm(listOf(owner), emptyList(), false) { form ->
                        savedId = io { books.createHolding(credentials.email, form) }
                    }
                }
            }
        }
        compose.onNodeWithTag("choice-Owner").performClick()
        compose.onNodeWithText(owner.name).performClick()
        compose.onNodeWithTag("choice-Asset category").performScrollTo().performClick()
        compose.onNodeWithText("MUTUAL_FUND").performClick()
        compose.onNodeWithTag("choice-MF plan").performScrollTo().performClick()
        compose.onNodeWithText("DIRECT").performClick()
        compose.onNodeWithTag("m27-holding-name").performScrollTo()
            .performTextInput("Fictional index fund")
        compose.onNodeWithTag("m27-holding-units").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("m27-review-holding").performScrollTo().performClick()
        assertNull(savedId)
        compose.onNodeWithTag("m27-confirm-holding").performScrollTo().performClick()
        assertNotNull(savedId)
        val before = io { books.read(credentials.email).holdings.single() }
        assertEquals(savedId, before.id)
        assertEquals(200_000_000L, before.quantityScaled)
        assertTrue(before.unknownCost)
        assertEquals("DIRECT", JSONObject(before.detailsJson!!).getString("mfPlan"))
        assertEquals(1, io { books.history(credentials.email, before.id).size })
        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(credentials.email, credentials.password))
        val after = NativeBooksRepository(reopened).read(credentials.email).holdings.single()
        assertTrue(after.unknownCost)
        assertEquals(200_000_000L, after.quantityScaled)
        assertEquals("DIRECT", JSONObject(after.detailsJson!!).getString("mfPlan"))
    }

    @Test fun tenComposeEventKindsWriteAppendOnlyLedgerAndSurviveEncryptedRestart() {
        val ctx = isolate()
        val (accounts, books) = open(ctx)
        val owner = io { books.read(credentials.email).people.single().id }
        val id = io { books.createHolding(credentials.email,
            NativeBooksRepository.NewHolding(owner, null, "Fictional equity", "EQUITY",
                "Other", 0L, 10_000L, "2024-01-01", false)) }
        val shown = io { books.read(credentials.email).holdings.single() }
        val revision = mutableStateOf(0)
        val written = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    key(revision.value) {
                        EventForm(listOf(shown), false, historyRevision = revision.value,
                            loadHistory = { h -> books.history(credentials.email, h) },
                            add = { form ->
                                val key = io { books.appendUserEvent(credentials.email, form) }
                                written += form.kind to key
                            },
                            price = { h, value, date ->
                                io { books.recordManualPrice(credentials.email, h, value, date) }
                            })
                    }
                }
            }
        }
        fun execute(kind: String, qty: String? = null, price: String? = null,
            amount: String? = null, fee: String? = null) {
            compose.onNodeWithTag("choice-Holding").performScrollTo().performClick()
            compose.onNodeWithText("Fictional equity • " +
                io { books.read(credentials.email).people.single().name }).performClick()
            if (kind != "BUY") {
                compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
                compose.onNodeWithText(kind).performClick()
            }
            if (kind == "CORRECTION") {
                val valuation = written.single { it.first == "VALUATION_UPDATE" }.second
                compose.onNodeWithTag("choice-Recorded event to correct").performScrollTo()
                    .performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText(" • VALUATION_UPDATE • " + valuation,
                        substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(" • VALUATION_UPDATE • " + valuation,
                    substring = true).performClick()
            }
            if (kind != "CORRECTION") {
                compose.onNodeWithTag("m27-event-date").performScrollTo()
                    .performTextReplacement("2025-01-02")
            }
            if (qty != null) compose.onNodeWithTag("m27-event-units")
                .performScrollTo().performTextReplacement(qty)
            if (price != null) compose.onNodeWithTag("m27-event-price")
                .performScrollTo().performTextReplacement(price)
            if (amount != null) compose.onNodeWithTag("m27-event-amount")
                .performScrollTo().performTextReplacement(amount)
            if (fee != null) compose.onNodeWithTag("m27-event-fee")
                .performScrollTo().performTextReplacement(fee)
            val before = written.size
            compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
            assertEquals(before, written.size)
            compose.onNodeWithTag("m27-confirm-event").performScrollTo().performClick()
            assertEquals(before + 1, written.size)
            compose.runOnUiThread { revision.value += 1 }
        }
        execute("OPENING_BALANCE", qty="1", price="10", amount="10")
        execute("BUY", qty="2", price="10", amount="20", fee="0.2")
        execute("SELL", qty="1", price="15", amount="15", fee="0.1")
        execute("BONUS", qty="1")
        execute("SPLIT", qty="2", price="1")
        execute("DIVIDEND", amount="1.5")
        execute("INTEREST", amount="0.5")
        execute("REPAYMENT", amount="1")
        execute("VALUATION_UPDATE", price="25")
        execute("CORRECTION", price="30")
        val events = io { books.history(credentials.email, id) }
        assertEquals(10, events.size)
        assertEquals(10, written.map { it.first }.distinct().size)
        assertEquals(600_000_000L, io {
            books.read(credentials.email).holdings.single().quantityScaled
        })
        assertEquals(300_000L, io {
            books.read(credentials.email).holdings.single().marketPriceMinor
        })
        assertEquals(1, events.count { it.kind == "CORRECTION" })
        assertEquals(written.single { it.first == "VALUATION_UPDATE" }.second,
            events.single { it.kind == "CORRECTION" }.supersedes)
        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(credentials.email, credentials.password))
        val other = NativeBooksRepository(reopened)
        assertEquals(10, other.history(credentials.email, id).size)
        assertEquals(600_000_000L, other.read(credentials.email).holdings.single().quantityScaled)
        assertEquals(300_000L, other.read(credentials.email).holdings.single().marketPriceMinor)
    }

    @Test fun newlySavedEventAppearsInSameCorrectionPickerWithoutLeavingScreen() {
        val (_, books) = open(isolate())
        val owner = io { books.read(credentials.email).people.single().id }
        val holdingId = io { books.createHolding(credentials.email,
            NativeBooksRepository.NewHolding(owner, null, "Fictional savings", "CASH_BANK",
                "Other", 0L, 0L, "2024-01-01", false)) }
        val revision = mutableStateOf(0)
        var saved: String? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EventForm(listOf(io { books.read(credentials.email).holdings.single() }),
                        false, historyRevision = revision.value,
                        loadHistory = { h -> books.history(credentials.email, h) },
                        add = { form ->
                            saved = io { books.appendUserEvent(credentials.email, form) }
                            revision.value += 1
                        }, price = { _, _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("choice-Holding").performClick()
        compose.onNodeWithText("Fictional savings • Fictional").performClick()
        compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
        compose.onNodeWithText("DIVIDEND").performClick()
        compose.onNodeWithTag("m27-event-date").performScrollTo()
            .performTextReplacement("2025-01-02")
        compose.onNodeWithTag("m27-event-amount").performScrollTo()
            .performTextReplacement("2")
        compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-event").performScrollTo().performClick()
        assertNotNull(saved)
        compose.onNodeWithTag("choice-Event kind").performScrollTo().performClick()
        compose.onNodeWithText("CORRECTION").performClick()
        compose.onNodeWithTag("choice-Recorded event to correct").performScrollTo()
            .performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(" • DIVIDEND • " + saved,
                substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(" • DIVIDEND • " + saved, substring = true).performClick()
        compose.onNodeWithTag("m27-event-amount").performScrollTo()
            .performTextReplacement("3")
        compose.onNodeWithTag("m27-review-event").performScrollTo().performClick()
        compose.onNodeWithTag("m27-confirm-event").performScrollTo().performClick()
        assertEquals(2, io { books.history(credentials.email, holdingId).size })
    }
}
