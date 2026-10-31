package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
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

/** Real NativeBooksScreen composable with sandboxed owner-scoped SQLCipher. */
class NativeM26OwnerLensNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun navigateTo(destination: String) {
        val main = NativeBooksMainDestination(destination)
        compose.onNodeWithTag("books-tab-$main").performClick()
        if (destination in listOf("Accounts", "Settings", "Records")) {
            compose.onNodeWithTag("books-subtab-$destination").performClick()
        }
    }
    private fun sandbox(): Context {
        val base = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val root = File(base.cacheDir, "m26-lens-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
        }
    }

    @Test fun selectedMemberLensScopesFamilyAccountsGoalsAndHoldings() {
        val context = sandbox()
        val gate = NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }
        val accounts = NativeAccountRepository(context, gate)
        val credentials = NativeCredentials.Enrollment(
            "owner@example.org", "Owner", "Family", "Strong!Pass123",
            "school", "First School", "pet", "First Pet")
        accounts.enrollVerified(credentials,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(credentials.email, credentials.password))
        val books = NativeBooksRepository(accounts)
        val primary = books.read(credentials.email).people.single().id
        val other = books.createPerson(credentials.email,
            NativeBooksRepository.NewPerson("Member Alpha", "Spouse"))
        val ownerCustody = books.createCustody(credentials.email, NativeBooksRepository.NewCustody(
            primary, "DEMAT", "Owner Custodian"))
        val otherCustody = books.createCustody(credentials.email, NativeBooksRepository.NewCustody(
            other, "DEMAT", "Other Custodian"))
        books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            primary, ownerCustody, "Owner Index", "MUTUAL_FUND", "Index / ETF",
            0L, 10000L, "2024-01-01", false))
        books.createHolding(credentials.email, NativeBooksRepository.NewHolding(
            other, otherCustody, "Alpha Index", "MUTUAL_FUND", "Index / ETF",
            0L, 10000L, "2024-01-01", false))
        books.createGoal(credentials.email, primary, "Owner Goal", 100000L,
            "2032-01-01", "HIGH")
        books.createGoal(credentials.email, other, "Other Goal", 200000L,
            "2032-01-01", "HIGH")
        compose.setContent {
            NativeBooksScreen(credentials.email, books,
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
        compose.onNodeWithText("Whole household").performClick()
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onAllNodes(hasText("Alpha Index", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Owner Index", substring = true)).assertCountEquals(0)
        navigateTo("Holdings")
        compose.onAllNodes(hasText("Alpha Index", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Owner Index", substring = true)).assertCountEquals(0)
        navigateTo("Records")
        compose.onNodeWithText("Money events and manual prices").assertExists()
        navigateTo("Family")
        compose.onAllNodes(hasText("Member Alpha •", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Owner Family •", substring = true)).assertCountEquals(0)
        navigateTo("Accounts")
        compose.onAllNodes(hasText("Other Custodian", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Owner Custodian", substring = true)).assertCountEquals(0)
        navigateTo("Goals")
        compose.onAllNodes(hasText("Other Goal", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Owner Goal", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onNodeWithText("Whole household").performClick()
        compose.onAllNodes(hasText("Owner Goal", substring = true)).assertCountEquals(1)
        navigateTo("Home")
        compose.onAllNodes(hasText("Owner Index", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("Alpha Index", substring = true)).assertCountEquals(1)
    }
}
