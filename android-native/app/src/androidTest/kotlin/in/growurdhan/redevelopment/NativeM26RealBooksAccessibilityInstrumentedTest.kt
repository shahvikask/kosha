package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Actual encrypted native books UI; never takes real-vault screenshots. */
class NativeM26RealBooksAccessibilityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun sandbox(): Context {
        val base = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val root = File(base.cacheDir, "m26-real-a11y-" + UUID.randomUUID())
            .apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }

    private fun verify(darkTheme: Boolean) {
        val context = sandbox()
        val gate = NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }
        val accounts = NativeAccountRepository(context, gate)
        val credentials = NativeCredentials.Enrollment(
            "synthetic.owner@example.org", "Owner", "Family", "Strong!Pass123",
            "school", "First School", "pet", "First Pet")
        accounts.enrollVerified(credentials,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(credentials.email, credentials.password))
        val books = NativeBooksRepository(accounts)
        books.createPerson(credentials.email,
            NativeBooksRepository.NewPerson("Member Alpha", "Spouse"))
        val primary = books.read(credentials.email).people.single { it.isPrimary }.id
        val reviewedHybrid = books.createHolding(credentials.email,
            NativeBooksRepository.NewHolding(primary, null, "Reviewed hybrid demo",
                "MUTUAL_FUND", "Hybrid fund", 100_000_000L, 1_010_000L,
                java.time.LocalDate.now().toString(), true))

        compose.setContent {
            GudTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        NativeBooksScreen(credentials.email, books,
                            NativeStatementImportRepository(accounts),
                            NativeInterestPayoutRepository(accounts),
                            NativeAnnualInterestCreditRepository(accounts),
                            NativePrivateDiagnosticsRepository(accounts),
                            NativeCasPdfEngine(context),
                            null, "", {}, {}, null, "", {}, {}, {}, {},
                            settings = NativeUserSettingsRepository(accounts))
                    }
                }
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("owner-lens-status"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Viewing Whole household financial records").assertExists()
        compose.onNodeWithTag("books-tab-Home").assertIsSelected()
        compose.onNodeWithTag("m29-home-health").assertExists()
        compose.onNodeWithTag("m29-home-price-health").assertExists()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m29-finance-priced"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m29-finance-priced").assertExists()
        compose.onNodeWithTag("m29-finance-unrealized").assertExists()
        compose.onNodeWithTag("m29-finance-realized").assertExists()
        compose.onNodeWithTag("m29-finance-exclusions").assertExists()
        compose.onNodeWithTag("m29-finance-category-MUTUAL_FUND").assertExists()
        compose.onNodeWithTag("m29-finance-income-empty").assertExists()
        compose.onNodeWithTag("e09-liability-separated").assertExists()
        compose.onNodeWithTag("e09-unverified-series").assertExists()
        compose.onNodeWithTag("e09-concentration-title").assertExists()
        compose.onNodeWithTag("e09-category-MUTUAL_FUND").assertExists()
        compose.onNodeWithTag("m29-home-categories").assertExists()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Reviewed hybrid subset: 0 reviewed", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m29-exposure-counts").assertExists()
        compose.onNodeWithTag("m29-exposure-picker").performScrollTo()
        compose.onNodeWithTag("m29-exposure-picker").performClick()
        compose.onNodeWithTag("m29-exposure-holding-$reviewedHybrid").performClick()
        compose.onNodeWithTag("m29-exposure-split").performScrollTo()
        compose.onNodeWithTag("m29-exposure-split").performTextInput("EQUITY:6500,DEBT:3500")
        compose.onNodeWithTag("m29-exposure-source").performScrollTo()
        compose.onNodeWithTag("m29-exposure-source").performTextInput("Owner factsheet review")
        compose.onNodeWithTag("m29-exposure-date").performScrollTo()
        compose.onNodeWithTag("m29-exposure-date")
            .performTextInput(java.time.LocalDate.now().toString())
        compose.onNodeWithTag("m29-exposure-save").performScrollTo()
        compose.onNodeWithTag("m29-exposure-save").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Reviewed hybrid subset: 1 reviewed", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m29-exposure-bucket-EQUITY").assertExists()
        compose.onNodeWithTag("m29-exposure-bucket-DEBT").assertExists()
        for (tab in listOf("Home", "Holdings", "Family", "Accounts",
            "Goals", "Records", "Documents", "Settings")) {
            // At 200% text scale the nav begins below the viewport. A clipped
            // off-screen Compose semantics node returns a 0px bound, which is
            // NOT the button's physical touch-target size.
            compose.onNodeWithTag("books-tab-$tab").performScrollTo()
            val nav = compose.onNodeWithTag("books-nav")
                .fetchSemanticsNode().boundsInRoot
            val bounds = compose.onNodeWithTag("books-tab-$tab")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$tab is clipped horizontally on the compact books screen",
                bounds.left >= nav.left - 1f && bounds.right <= nav.right + 1f)
            assertTrue("$tab has less than a 48dp touch target after scrolling: " +
                "height=${bounds.height}px at synthetic density=1",
                bounds.height >= 48f)
        }

        // Real, authenticated Settings at 360dp / 200% in both themes.
        compose.onNodeWithTag("books-tab-Settings").performScrollTo()
        compose.onNodeWithTag("books-tab-Settings").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m31-theme-SYSTEM"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-theme-SYSTEM").assertIsNotEnabled()
        compose.onNodeWithTag("m31-portfolio-sort-CATEGORY").performScrollTo()
        compose.onNodeWithTag("m31-portfolio-sort-CATEGORY").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Default: Category sort", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-portfolio-archive-ACTIVE").performScrollTo()
        compose.onNodeWithTag("m31-portfolio-archive-ACTIVE").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Active archive view", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-portfolio-home-COMPACT").performScrollTo()
        compose.onNodeWithTag("m31-portfolio-home-COMPACT").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("6 Home rows", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-portfolio-defaults").assertExists()
        compose.onNodeWithTag("m31-family-settings").performScrollTo()
        compose.onNodeWithTag("m31-family-settings").performClick()
        compose.onNodeWithText("Family members").assertExists()
        compose.onNodeWithTag("books-tab-Settings").performScrollTo()
        compose.onNodeWithTag("books-tab-Settings").performClick()
        compose.onNodeWithTag("m31-custody-settings").performScrollTo()
        compose.onNodeWithTag("m31-custody-settings").performClick()
        compose.onNodeWithText("Custody accounts").assertExists()
        compose.onNodeWithTag("books-tab-Settings").performScrollTo()
        compose.onNodeWithTag("books-tab-Settings").performClick()
        compose.onNodeWithTag("m31-account-auth").assertExists()
        compose.onNodeWithTag("m31-sgb-RBI_REFERENCE_REQUIRED").assertExists()
        compose.onNodeWithTag("m31-ibja-OWNER_REVIEW_REQUIRED").assertExists()
        compose.onNodeWithTag("m31-device-loss-warning").assertExists()
        // P37/E16: authenticated Settings exposes only a read-only Play catalog.
        // A product listing never proves account entitlement or grants access.
        compose.onNodeWithTag("p37-play-unconfigured").assertExists()
        compose.onNodeWithText("Google Play Billing is not connected in this build.",
            substring = true).assertExists()
        compose.onNodeWithTag("p37-check-play-catalog").performScrollTo()
        compose.onNodeWithTag("p37-check-play-catalog").assertExists()
        compose.onNodeWithTag("p37-play-catalog-status").assertExists()
        compose.onNodeWithText("Verification status: unavailable", substring = true).assertExists()
        val probeBounds = compose.onNodeWithTag("p37-check-play-catalog")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Read-only Play catalog control smaller than 48dp at density=1",
            probeBounds.height >= 48f)


        compose.onNodeWithTag("m31-support-copy").assertDoesNotExist()
        compose.onNodeWithTag("m31-account-lock").assertExists()
        compose.onNodeWithTag("m31-ibja-OWNER_REVIEW_REQUIRED").performScrollTo()
        compose.onNodeWithTag("m31-ibja-OWNER_REVIEW_REQUIRED").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m31-ibja-empty"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-ibja-date").performScrollTo()
        compose.onNodeWithTag("m31-ibja-date").performTextInput("2025-06-01")
        compose.onNodeWithTag("m31-ibja-rate").performScrollTo()
        compose.onNodeWithTag("m31-ibja-rate").performTextInput("12345.6789")
        compose.onNodeWithTag("m31-ibja-save").performScrollTo()
        compose.onNodeWithTag("m31-ibja-save").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m31-ibja-record"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-ibja-delete").performScrollTo()
        compose.onNodeWithTag("m31-ibja-delete").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m31-ibja-empty"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Opt in to on-device technical checks").performScrollTo()
        compose.onNodeWithText("Opt in to on-device technical checks").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Technical diagnostics: enabled"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("View sanitized health locally").performScrollTo()
        compose.onNodeWithText("View sanitized health locally").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m31-redacted-report"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-support-copy").performScrollTo()
        compose.onNodeWithTag("m31-support-copy").performClick()
        compose.onNodeWithTag("m31-support-delete-copy").assertExists()
        compose.onNodeWithTag("m31-support-clear").performScrollTo()
        compose.onNodeWithTag("m31-support-clear").performClick()
        compose.onNodeWithTag("m31-redacted-report").assertDoesNotExist()
        compose.onNodeWithTag("m31-support-delete-copy").assertDoesNotExist()
        compose.onNodeWithText("Opt out and clear local diagnostics").performScrollTo()
        compose.onNodeWithText("Opt out and clear local diagnostics").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Technical diagnostics: disabled (default)"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m31-theme-DARK").performScrollTo()
        compose.onNodeWithTag("m31-theme-DARK").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("m31-theme-DARK").assertIsNotEnabled()
        // A technical support clipboard action cannot appear without opt-in and preview.
        compose.onNodeWithTag("m31-support-copy").assertDoesNotExist()
        compose.onNodeWithTag("books-tab-Home").performScrollTo()
        compose.onNodeWithTag("books-tab-Home").performClick()
        compose.onNodeWithText("Home preview: up to 6 owner-scoped holding(s)").assertExists()
        // The production Holdings destination must expose the manual-only official
        // close action, not merely a standalone synthetic component.
        compose.onNodeWithTag("books-tab-Holdings").performScrollTo()
        compose.onNodeWithTag("books-tab-Holdings").performClick()
        compose.onNodeWithTag("m31-holdings-defaults").assertExists()
        compose.onNodeWithText("Saved defaults: Category sort, Active archived filter; " +
            "manual view choices override until next vault unlock.").assertExists()
        compose.onNodeWithTag("m30-source-NSE").assertExists()
        compose.onNodeWithTag("m30-source-BSE").assertExists()
        compose.onNodeWithTag("m30-import-close").assertIsNotEnabled()
        compose.onNodeWithTag("books-tab-Home").performScrollTo()
        compose.onNodeWithTag("books-tab-Home").performClick()
        compose.onNodeWithText("Whole household").performScrollTo()
        compose.onNodeWithText("Whole household").performClick()
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onNodeWithText("Viewing Member: Member Alpha financial records")
            .assertExists()
        compose.onNodeWithText("0 holdings • 1 active member in view").assertExists()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("m29-finance-empty"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m29-finance-category-MUTUAL_FUND").assertDoesNotExist()
        compose.onNodeWithTag("m29-finance-income-empty").assertExists()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Reviewed hybrid subset: 0 reviewed", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("m29-exposure-bucket-EQUITY").assertDoesNotExist()
        compose.onNodeWithTag("books-tab-Holdings").performScrollTo()
        compose.onNodeWithTag("books-tab-Holdings").performClick()
        compose.onNodeWithTag("m30-source-url").assertDoesNotExist()
        compose.onNodeWithTag("m30-owner-scope").assertExists()
        compose.onNodeWithTag("books-tab-Family").performScrollTo()
        compose.onNodeWithTag("books-tab-Family").performClick()
        compose.onNodeWithTag("m31-dossier-scope").assertExists()
        compose.onNodeWithTag("m31-dossier-preview").assertDoesNotExist()
        compose.onNodeWithTag("m31-dossier-reveal").assertIsNotEnabled()
        compose.onNodeWithTag("m31-dossier-ack").performScrollTo()
        compose.onNodeWithTag("m31-dossier-ack").performClick()
        compose.onNodeWithTag("m31-dossier-reveal").performScrollTo()
        compose.onNodeWithTag("m31-dossier-reveal").performClick()
        compose.onNodeWithTag("m31-dossier-preview").assertExists()
        compose.onNodeWithText("1 member(s), 0 account(s), 0 active holding(s)").assertExists()
        compose.onNodeWithTag("m31-dossier-hide").performScrollTo()
        compose.onNodeWithTag("m31-dossier-hide").performClick()
        compose.onNodeWithTag("m31-dossier-preview").assertDoesNotExist()
        compose.onNodeWithTag("m31-dossier-reveal").assertIsNotEnabled()
    }

    private fun verifyHindiOnboardingAtTwoHundredPercent(dark: Boolean) {
        compose.setContent {
            GudTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(360.dp).height(620.dp)) {
                        NativeOnboardingLanguageNotice()
                    }
                }
            }
        }
        compose.onNodeWithTag("m31-hindi-local-only").assertDoesNotExist()
        compose.onNodeWithTag("m31-hindi-toggle").performClick()
        compose.onNodeWithTag("m31-hindi-local-only").assertExists()
        compose.onNodeWithTag("m31-hindi-loss-warning").assertExists()
        compose.onNodeWithTag("m31-hindi-terms-distinct").assertExists()
        compose.onNodeWithTag("m31-hindi-toggle").performClick()
        compose.onNodeWithTag("m31-hindi-loss-warning").assertDoesNotExist()
    }

    @Test fun bilingualOnboardingLight360dpTwoHundredPercentIsOptional() =
        verifyHindiOnboardingAtTwoHundredPercent(dark = false)

    @Test fun bilingualOnboardingDark360dpTwoHundredPercentIsOptional() =
        verifyHindiOnboardingAtTwoHundredPercent(dark = true)

    @Test fun realBooksCompactLightTwoHundredPercentOwnerLensAccessible() =
        verify(darkTheme = false)

    @Test fun realBooksCompactDarkTwoHundredPercentOwnerLensAccessible() =
        verify(darkTheme = true)
}
