package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Actual SQLCipher-backed navigation. Switching owner lenses must invalidate
 * a previously selected money-event holding, not just filter the visible list.
 */
class NativeM26OwnerLensMutationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun sandbox(): Context {
        val base = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val root = File(base.cacheDir, "m26-mutation-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }

    @Test fun ownerSwitchRevokesStaleMoneyEventAndManualPriceSelection() {
        val context = sandbox()
        val gate = NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }
        val accounts = NativeAccountRepository(context, gate)
        val input = NativeCredentials.Enrollment(
            "owner@example.org", "Owner", "Family", "Strong!Pass123",
            "school", "First School", "pet", "First Pet")
        accounts.enrollVerified(input,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(input.email, input.password))
        val books = NativeBooksRepository(accounts)
        val primary = books.read(input.email).people.single().id
        val other = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Member Alpha", "Spouse"))
        val firstAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(primary, "DEMAT", "Primary Custodian"))
        val secondAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(other, "DEMAT", "Alpha Custodian"))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, firstAccount, "Owner Fund", "MUTUAL_FUND", "Index / ETF",
            0L, 10000L, "2024-01-01", false))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            other, secondAccount, "Alpha Fund", "MUTUAL_FUND", "Index / ETF",
            0L, 10000L, "2024-01-01", false))
        compose.setContent {
            NativeBooksScreen(input.email, books,
                NativeStatementImportRepository(accounts),
                NativeInterestPayoutRepository(accounts),
                NativeAnnualInterestCreditRepository(accounts),
                NativePrivateDiagnosticsRepository(accounts),
                NativeCasPdfEngine(context),
                null, "", {}, {}, null, "", {}, {}, {}, {})
        }
        compose.waitUntil(30000) {
            compose.onAllNodes(hasText("Whole household")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Records").performClick()
        compose.onNodeWithTag("choice-Holding").performClick()
        compose.onNodeWithText("Alpha Fund • Member Alpha").performClick()
        compose.onNodeWithText("Append money event").assertIsEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsEnabled()

        compose.onNodeWithText("Whole household").performClick()
        compose.onNodeWithText("Owner Family").performClick()
        compose.onNodeWithText("Records").performClick()
        compose.onNodeWithText("Append money event").assertIsNotEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsNotEnabled()

        compose.onNodeWithText("Owner Family").performClick()
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onNodeWithText("Records").performClick()
        compose.onNodeWithText("Append money event").assertIsNotEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsNotEnabled()
    }
}
