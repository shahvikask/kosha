package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                            null, "", {}, {}, null, "", {}, {}, {}, {})
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
        compose.onNodeWithTag("m29-home-categories").assertExists()
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

        compose.onNodeWithText("Whole household").performScrollTo()
        compose.onNodeWithText("Whole household").performClick()
        compose.onNodeWithText("Member Alpha").performClick()
        compose.onNodeWithText("Viewing Member: Member Alpha financial records")
            .assertExists()
        compose.onNodeWithText("0 holdings • 1 active member in view").assertExists()
    }

    @Test fun realBooksCompactLightTwoHundredPercentOwnerLensAccessible() =
        verify(darkTheme = false)

    @Test fun realBooksCompactDarkTwoHundredPercentOwnerLensAccessible() =
        verify(darkTheme = true)
}
