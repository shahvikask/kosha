package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** P36 real Compose scroll + opt-in, isolated synthetic SQLCipher vault only. */
class NativePrivacyConsentUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun privacyCannotBeAcceptedWithoutEndOfNoticeAndExplicitCheckbox() {
        val base = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val root = File(base.cacheDir, "p36-ui-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
        val admission = NativeDeviceAdmission().also {
            it.onStart()
            check(it.begin() && it.complete(true))
        }
        val account = NativeAccountRepository(context, admission)
        val credentials = NativeCredentials.Enrollment(
            "privacy.ui@example.org", "Privacy", "Owner", "Strong!Pass123",
            "school", "First School", "pet", "Cat")
        account.enrollVerified(credentials,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-29T12:00:00Z", true))
        check(account.login(credentials.email, credentials.password))
        val repo = NativePrivacyConsentRepository(account)
        compose.setContent {
            GudTheme {
                Box(Modifier.width(360.dp).height(650.dp).testTag("p36-ui-root")) {
                    NativePrivacyConsentCard(credentials.email, repo)
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("p36-privacy-accept"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("p36-privacy-accept").assertIsNotEnabled()
        compose.onNodeWithTag("p36-privacy-checkbox").performClick()
        compose.onNodeWithTag("p36-privacy-accept").assertIsNotEnabled()
        repeat(12) {
            compose.onNodeWithTag("p36-privacy-scroller").performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("p36-privacy-accept").assertIsEnabled()
        compose.onNodeWithTag("p36-privacy-accept").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("p36-privacy-accepted"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertNotNull(repo.accepted(credentials.email))
    }
}
