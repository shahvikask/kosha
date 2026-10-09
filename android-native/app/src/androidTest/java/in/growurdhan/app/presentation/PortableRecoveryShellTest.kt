package `in`.growurdhan.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableRecoveryShellTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newVaultOffersNonDestructiveRecoveryWithoutCreatingDatabase() {
        var vaultAction: VaultIntent? = null
        var recoveryAction: PortableRecoveryIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(),
                    onIntent = {},
                    vaultState = VaultUiState.NotCreated,
                    onVaultIntent = { vaultAction = it },
                    onRecoveryIntent = { recoveryAction = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Recover from encrypted backup").performClick()
        compose.onNodeWithText("Backup & recovery").assertIsDisplayed()
        compose.onNodeWithText("Restore encrypted backup").performClick()
        compose.runOnIdle {
            assertEquals(null, vaultAction)
            assertEquals(PortableRecoveryIntent.SelectRestoreSource, recoveryAction)
        }
    }

    @Test fun restorePassphraseNeedsTwelveCharactersBeforeCredentialRequest() {
        var recoveryAction: PortableRecoveryIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(),
                    onIntent = {},
                    vaultState = VaultUiState.NotCreated,
                    recoveryState = PortableRecoveryUiState.EnterPassphrase(
                        PortableRecoveryMode.RESTORE,
                    ),
                    onRecoveryIntent = { recoveryAction = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Recover from encrypted backup").performClick()
        compose.onNodeWithText("Confirm device lock and restore").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Backup passphrase entry").performTextInput("synthetic12characters")
        compose.onNodeWithText("Confirm device lock and restore").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(
                PortableRecoveryIntent.ConfirmPassphrase(
                    "synthetic12characters", "",
                ),
                recoveryAction,
            )
        }
    }
}
