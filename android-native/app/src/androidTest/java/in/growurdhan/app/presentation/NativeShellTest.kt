package `in`.growurdhan.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeShellTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsNavigationAndThemeIntent() {
        var lastIntent: AppIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(theme = ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)),
                    onIntent = { lastIntent = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals(AppIntent.SelectTheme(ThemeMode.DARK), lastIntent) }
    }

    @Test
    fun vaultIsCreatedOnlyAfterExplicitUserAction() {
        var action: VaultIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(),
                    onIntent = {},
                    vaultState = VaultUiState.NotCreated,
                    onVaultIntent = { action = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("No native vault found").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, action) }
        compose.onNodeWithText("Create encrypted vault").performClick()
        compose.runOnIdle { assertEquals(VaultIntent.OpenOrCreate, action) }
    }

    @Test
    fun privacyDestinationShowsActualStorageStatus() {
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(AppUiState(), onIntent = {})
            }
        }
        compose.onNodeWithContentDescription("Privacy").performClick()
        compose.onNodeWithText("Privacy by construction").assertIsDisplayed()
        compose.onNodeWithText("Preferences on this device").assertIsDisplayed()
    }
}
