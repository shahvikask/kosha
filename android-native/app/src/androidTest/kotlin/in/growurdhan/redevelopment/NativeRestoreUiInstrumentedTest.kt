package `in`.growurdhan.redevelopment

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.designsystem.GudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic callbacks only: does not substitute for real Keyguard/SQLCipher verification. */
@RunWith(AndroidJUnit4::class)
class NativeRestoreUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fileSelectionDeniedWithoutRealDeviceAdmission() {
        var selected = 0
        compose.setContent {
            GudTheme {
                NativeRestoreScreen(
                    deviceVerified = false, staged = false,
                    previewAction = { null }, publishAction = { _, _ -> false },
                    selectEncryptedFile = { selected++ }, onBack = {}
                )
            }
        }
        compose.onNodeWithText("Choose encrypted .kosha backup")
            .assertExists().assertIsNotEnabled()
        assertEquals(0, selected)
    }

    @Test fun restoreRequiresExplicitConfirmationAfterPreview() {
        var published = 0
        compose.setContent {
            GudTheme {
                NativeRestoreScreen(
                    deviceVerified = true, staged = true,
                    previewAction = {
                        NativeRestoreFlow.Preview("synthetic-only", 1, 2, 3)
                    },
                    publishAction = { _, _ -> published++; true },
                    selectEncryptedFile = {}, onBack = {}
                )
            }
        }
        compose.onNodeWithText("Verify identity and preview")
            .performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Confirm one-time restore")
            .performScrollTo().assertIsNotEnabled()
        assertEquals(0, published)
    }
}
