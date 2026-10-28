package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** P12 actual Compose editor -> encrypted SQLCipher -> restart; synthetic only. */
@RunWith(AndroidJUnit4::class)
class NativeM27HoldingArchiveJourneyInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val credential = NativeCredentials.Enrollment(
        "owner@example.org", "Fictional", "Household", "Strong!Pass123",
        "school", "First School", "pet", "First Pet")

    private fun context(): Context {
        val parent = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(parent.cacheDir, "m27-p12-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(parent) {
            override fun getDatabasePath(name: String) =
                File(path, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() = File(path, "no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(path, "files").also { it.mkdirs() }
        }
    }
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun enroll(ctx: Context): Pair<NativeAccountRepository, NativeBooksRepository> {
        val account = NativeAccountRepository(ctx, gate())
        account.enrollVerified(credential,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-27T00:00:00Z", true))
        assertTrue(account.login(credential.email, credential.password))
        return account to NativeBooksRepository(account)
    }

    @Test fun composeGoalLocationArchiveCancelConfirmAndReopenSurviveEncryptedRestart() {
        val ctx = context()
        val (account, books) = enroll(ctx)
        val person = books.read(credential.email).people.single().id
        val custody = books.createCustody(credential.email,
            NativeBooksRepository.NewCustody(person, "DEMAT", "Fictional custody"))
        val goal = books.createGoal(credential.email, person, "Retirement target",
            100_000L, "2035-01-01", "MEDIUM")
        val holdingId = books.createHolding(credential.email,
            NativeBooksRepository.NewHolding(person, custody, "Fictional share", "EQUITY",
                "Other", 100_000_000L, 10_000L, "2024-01-01", true,
                openingUnitPriceMinor = 10_000L))
        val sale = books.appendUserEvent(credential.email,
            NativeBooksRepository.NewEvent(holdingId, "SELL", "2024-06-01",
                100_000_000L, 20_000L, 20_000L, 100L))
        val beforeIds = books.history(credential.email, holdingId).map { it.id }
        assertTrue(sale in beforeIds)
        assertEquals(0L, books.read(credential.email).holdings.single().quantityScaled)

        var liveBooks = books
        val state = mutableStateOf(liveBooks.read(credential.email))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HoldingEditForm(state.value.holdings, state.value.custody,
                        state.value.goals, false,
                        update = { id, accountId, notes, details, location ->
                            liveBooks.updateHoldingMetadata(credential.email, id,
                                accountId, notes, details, location)
                            state.value = liveBooks.read(credential.email)
                        },
                        assign = { id, linked ->
                            liveBooks.assignGoal(credential.email, id, linked)
                            state.value = liveBooks.read(credential.email)
                        },
                        archive = { id, value ->
                            liveBooks.setHoldingArchived(credential.email, id, value)
                            state.value = liveBooks.read(credential.email)
                        })
                }
            }
        }
        compose.onNodeWithTag("choice-Holding to edit").performScrollTo().performClick()
        compose.onNodeWithText("Fictional share").performClick()
        compose.onNodeWithTag("choice-Linked goal").performScrollTo().performClick()
        compose.onNodeWithText("Retirement target").performClick()
        compose.onNodeWithText("Set linked goal").performScrollTo().performClick()
        assertEquals(goal, books.read(credential.email).holdings.single().goalId)

        compose.onNodeWithTag("m27-edit-notes").performScrollTo()
            .performTextReplacement("Review note, not a financial event")
        compose.onNodeWithTag("m27-edit-location").performScrollTo()
            .performTextReplacement("Fictional address")
        compose.onNodeWithText("Save non-ledger metadata").performScrollTo().performClick()
        val saved = books.read(credential.email).holdings.single()
        assertEquals("Fictional address", saved.location)
        assertEquals("Review note, not a financial event", saved.notes)
        assertEquals(custody, saved.accountId)

        compose.onNodeWithTag("m27-archive-action").performScrollTo().performClick()
        compose.onNodeWithText("Cancel archive").performScrollTo().performClick()
        assertFalse(books.read(credential.email).holdings.single().archived)
        compose.onNodeWithTag("m27-archive-action").performScrollTo().performClick()
        compose.onNodeWithTag("m27-archive-confirm").performScrollTo().performClick()
        assertTrue(books.read(credential.email).holdings.single().archived)
        assertEquals(beforeIds, books.history(credential.email, holdingId).map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(credential.email,
                NativeBooksRepository.NewEvent(holdingId, "BUY", "2025-01-01",
                    100_000_000L, 10_000L, 10_000L))
        }
        assertEquals(beforeIds, books.history(credential.email, holdingId).map { it.id })

        account.onBackground()
        val restarted = NativeAccountRepository(ctx, gate())
        assertTrue(restarted.login(credential.email, credential.password))
        val reopened = NativeBooksRepository(restarted)
        val data = reopened.read(credential.email).holdings.single()
        assertTrue(data.archived)
        assertEquals(goal, data.goalId)
        assertEquals("Fictional address", data.location)
        assertEquals(custody, data.accountId)
        assertEquals(beforeIds, reopened.history(credential.email, holdingId).map { it.id })
        // Reopen from the original Compose selection with refreshed, admitted repository.
        compose.runOnUiThread {
            liveBooks = reopened
            state.value = reopened.read(credential.email)
        }
        compose.onNodeWithTag("m27-archive-action").performScrollTo().performClick()
        assertFalse(reopened.read(credential.email).holdings.single().archived)
        // Previously COALESCE silently retained both fields after the editor
        // submitted blanks. Explicit clearing must persist through encryption.
        compose.onNodeWithTag("m27-edit-notes").performScrollTo()
            .performTextReplacement("")
        compose.onNodeWithTag("m27-edit-location").performScrollTo()
            .performTextReplacement("")
        compose.onNodeWithText("Save non-ledger metadata").performScrollTo().performClick()
        val cleared = reopened.read(credential.email).holdings.single()
        assertNull(cleared.notes)
        assertNull(cleared.location)
        assertEquals(goal, cleared.goalId)
        assertEquals(custody, cleared.accountId)
        assertEquals(beforeIds, reopened.history(credential.email, holdingId).map { it.id })
        restarted.onBackground()
        val relog = NativeAccountRepository(ctx, gate())
        assertTrue(relog.login(credential.email, credential.password))
        val afterSecondRestart = NativeBooksRepository(relog)
            .read(credential.email).holdings.single()
        assertNull(afterSecondRestart.notes)
        assertNull(afterSecondRestart.location)
        assertFalse(afterSecondRestart.archived)
        assertEquals(goal, afterSecondRestart.goalId)
    }

    @Test fun customDatedValuationCannotCreatePurchasesAndSurvivesReopen() {
        val ctx = context()
        val (account, books) = enroll(ctx)
        val owner = books.read(credential.email).people.single().id
        val id = books.createHolding(credential.email,
            NativeBooksRepository.NewHolding(owner, null, "Fictional dated fund",
                "MUTUAL_FUND", "Equity fund", 100_000_000L, 10_000L,
                "2024-01-01", false))
        val before = books.history(credential.email, id).map { it.id }
        books.recordManualPrice(credential.email, id, 42_000L, "2025-01-12")
        books.recordManualPrice(credential.email, id, 31_000L, "2024-12-01")
        assertEquals(42_000L, books.read(credential.email).holdings.single().marketPriceMinor)
        assertEquals(before, books.history(credential.email, id).map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            books.recordManualPrice(credential.email, id, -1L, "2025-01-13")
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.recordManualPrice(credential.email, id, 70_000L, "2099-01-01")
        }
        assertEquals(before, books.history(credential.email, id).map { it.id })
        account.onBackground()
        val reopenedAccount = NativeAccountRepository(ctx, gate())
        assertTrue(reopenedAccount.login(credential.email, credential.password))
        val reopened = NativeBooksRepository(reopenedAccount)
        val state = reopened.read(credential.email).holdings.single()
        assertEquals(42_000L, state.marketPriceMinor)
        assertTrue(state.unknownCost)
        assertEquals(100_000_000L, state.quantityScaled)
        assertEquals(before, reopened.history(credential.email, id).map { it.id })
    }

    @Test fun openPositionAndForeignGoalAndCustodyFailWithoutDataLossOrReassignment() {
        val (_, books) = enroll(context())
        val primary = books.read(credential.email).people.single().id
        val spouse = books.createPerson(credential.email,
            NativeBooksRepository.NewPerson("Fictional Spouse", "Spouse"))
        val ownAccount = books.createCustody(credential.email,
            NativeBooksRepository.NewCustody(primary, "DEMAT", "Primary broker"))
        val foreignAccount = books.createCustody(credential.email,
            NativeBooksRepository.NewCustody(spouse, "DEMAT", "Foreign broker"))
        val foreignGoal = books.createGoal(credential.email, spouse,
            "Other member goal", 20_000L, "2032-01-01", "LOW")
        val id = books.createHolding(credential.email, NativeBooksRepository.NewHolding(
            primary, ownAccount, "Live fictional equity", "EQUITY", "Other",
            100_000_000L, 15_000L, "2024-01-01", true))
        val before = books.history(credential.email, id).map { it.id }
        assertThrows(IllegalArgumentException::class.java) {
            books.setHoldingArchived(credential.email, id, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(credential.email, id, foreignGoal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(credential.email, id,
                foreignAccount, "No unauthorized change", null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(credential.email, id,
                null, "Cannot falsify custody", null, null)
        }
        val after = books.read(credential.email).holdings.single { it.id == id }
        assertFalse(after.archived)
        assertNull(after.goalId)
        assertEquals(ownAccount, after.accountId)
        assertNull(after.notes)
        assertEquals(before, books.history(credential.email, id).map { it.id })
    }
}
