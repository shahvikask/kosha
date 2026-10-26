package `in`.growurdhan.redevelopment

import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** First-market-release scope: archived financial backup and restore stay inaccessible. */
@RunWith(AndroidJUnit4::class)
class NativeBaseReleaseScopeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun financialVaultBackupAndRestoreAreAbsentFromFirstRelease() {
        compose.onNodeWithText("Restore an encrypted backup on a new device")
            .assertDoesNotExist()
        compose.onNodeWithText("Restore a newer GUDNAT01 native encrypted backup")
            .assertDoesNotExist()
        compose.onNodeWithText("Choose native encrypted GUDNAT01 backup")
            .assertDoesNotExist()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Android automatic app-data backup must remain disabled", 0,
            ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
