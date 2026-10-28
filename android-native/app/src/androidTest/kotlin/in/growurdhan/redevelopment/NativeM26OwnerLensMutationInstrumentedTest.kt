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
import org.junit.Assert.*
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
        compose.onNodeWithTag("m27-review-event").assertIsEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsEnabled()

        compose.onNodeWithText("Whole household").performClick()
        compose.onNodeWithText("Owner Family").performClick()
        compose.onNodeWithText("Records").performClick()
        compose.onNodeWithTag("m27-review-event").assertIsNotEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsNotEnabled()

        compose.onNodeWithText("Owner Family").performClick()
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onNodeWithText("Records").performClick()
        compose.onNodeWithTag("m27-review-event").assertIsNotEnabled()
        compose.onNodeWithText("Save manual price and source date").assertIsNotEnabled()
    }

    private data class BooksFixture(val context: Context,
        val accounts: NativeAccountRepository, val books: NativeBooksRepository)

    private fun admittedBooks(): BooksFixture {
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
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-28T10:00:00Z", true))
        assertTrue(accounts.login(credentials.email, credentials.password))
        return BooksFixture(context, accounts, NativeBooksRepository(accounts))
    }

    @Test fun crossOwnerCustodyAndGoalMutationsRollbackThroughEncryptedRestart() {
        val fixture = admittedBooks()
        val email = "owner@example.org"
        val books = fixture.books
        val owner = books.read(email).people.single().id
        val member = books.createPerson(email,
            NativeBooksRepository.NewPerson("Synthetic Spouse", "Spouse"))
        val ownedCustody = books.createCustody(email, NativeBooksRepository.NewCustody(
            owner, "DEMAT", "Synthetic owner broker", "****1234",
            nominee = "Synthetic primary nominee"))
        val memberCustody = books.createCustody(email, NativeBooksRepository.NewCustody(
            member, "DEMAT", "Synthetic member broker", "****9876",
            nominee = "Synthetic member nominee"))
        val ownerGoal = books.createGoal(email, owner, "Owner Goal", 200_000L,
            "2032-01-01", "HIGH")
        val memberGoal = books.createGoal(email, member, "Member Goal", 300_000L,
            "2032-01-01", "HIGH")
        val memberHolding = books.createHolding(email,
            NativeBooksRepository.NewHolding(member, memberCustody,
                "Synthetic member stock", "EQUITY", "Large cap",
                0L, 0L, "2024-01-01", false))
        val ownerHolding = books.createHolding(email,
            NativeBooksRepository.NewHolding(owner, ownedCustody,
                "Synthetic owner stock", "EQUITY", "Large cap",
                0L, 0L, "2024-01-01", false))
        val before = books.read(email)
        assertThrows(IllegalArgumentException::class.java) {
            books.createHolding(email, NativeBooksRepository.NewHolding(
                member, ownedCustody, "Foreign linked stock", "EQUITY", "Large cap",
                0L, 0L, "2024-01-01", false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(email, memberHolding, ownedCustody,
                "Illicit owner transfer", null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateCustody(email, ownedCustody, NativeBooksRepository.NewCustody(
                member, "DEMAT", "Reassigned custodian", nominee = "Changed nominee"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(email, memberHolding, ownerGoal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(email, ownerHolding, memberGoal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateGoal(email, ownerGoal, member, "Reassigned goal",
                200_000L, "2032-01-01", "HIGH")
        }
        val after = books.read(email)
        assertEquals(before.people, after.people)
        assertEquals(before.custody, after.custody)
        assertEquals(before.goals, after.goals)
        assertEquals(before.holdings, after.holdings)
        assertTrue(books.history(email, memberHolding).isEmpty())
        fixture.accounts.onBackground()
        val gate = NativeDeviceAdmission().also {
            it.onStart(); check(it.begin() && it.complete(true))
        }
        val reopenedAccount = NativeAccountRepository(fixture.context, gate)
        assertTrue(reopenedAccount.login(email, "Strong!Pass123"))
        val reopened = NativeBooksRepository(reopenedAccount).read(email)
        assertEquals(after.people, reopened.people)
        assertEquals(after.custody, reopened.custody)
        assertEquals(after.goals, reopened.goals)
        assertEquals(after.holdings, reopened.holdings)
        assertNull(reopened.holdings.single { it.id == memberHolding }.goalId)
        assertEquals("Synthetic primary nominee",
            reopened.custody.single { it.id == ownedCustody }.nominee)
    }

    @Test fun primaryMinorGuardianAndHufReclassificationFailClosedAfterRestart() {
        val fixture = admittedBooks()
        val email = "owner@example.org"
        val books = fixture.books
        val primary = books.read(email).people.single().id
        val guardian = books.createPerson(email,
            NativeBooksRepository.NewPerson("Synthetic Adult", "Parent"))
        val huf = books.createPerson(email,
            NativeBooksRepository.NewPerson("Synthetic HUF", "HUF", kind = "HUF"))
        val child = books.createPerson(email, NativeBooksRepository.NewPerson(
            "Synthetic Child", "Child", dob = "2017-04-05", guardianId = guardian))
        val before = books.read(email)
        assertThrows(IllegalArgumentException::class.java) {
            books.createPerson(email, NativeBooksRepository.NewPerson(
                "Unrepresented child", "Child", dob = "2017-04-05"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updatePerson(email, child, NativeBooksRepository.NewPerson(
                "Synthetic Child", "Child", dob = "2017-04-05", guardianId = huf))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.archivePerson(email, guardian)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.archivePerson(email, primary)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updatePerson(email, primary,
                NativeBooksRepository.NewPerson("Owner Family", "HUF", kind = "HUF"))
        }
        assertEquals(before.people, books.read(email).people)
        fixture.accounts.onBackground()
        val gate = NativeDeviceAdmission().also {
            it.onStart(); check(it.begin() && it.complete(true))
        }
        val reopenedAccount = NativeAccountRepository(fixture.context, gate)
        assertTrue(reopenedAccount.login(email, "Strong!Pass123"))
        val loaded = NativeBooksRepository(reopenedAccount).read(email)
        assertEquals(before.people, loaded.people)
        assertTrue(loaded.people.single { it.id == guardian }.active)
        assertTrue(loaded.people.single { it.id == primary }.isPrimary)
        assertTrue(loaded.people.single { it.id == child }.active)
    }
}
