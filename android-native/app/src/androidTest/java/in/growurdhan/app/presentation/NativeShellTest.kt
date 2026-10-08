package `in`.growurdhan.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.HoldingId
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.finance.FinancialHolding
import `in`.growurdhan.domain.finance.ReviewedStatement
import `in`.growurdhan.domain.finance.SnapshotReconciler
import `in`.growurdhan.domain.finance.StatementLine
import java.time.LocalDate
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
        // Additional privacy copy moved this card below the initial viewport.
        // Test its actual visible, scrollable state rather than initial placement.
        compose.onNodeWithText("Preferences on this device")
            .performScrollTo()
            .assertIsDisplayed()
    }
    @Test fun emptyStatementDestinationCannotOfferUnverifiedImport() {
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(AppUiState(), onIntent = {})
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("No statement ready for review").assertIsDisplayed()
        compose.onNodeWithText("Confirm and authenticate approval").assertDoesNotExist()
    }

    @Test fun consentButtonNeedsExplicitAcknowledgement() {
        val owner = OwnerId("self")
        val holdingId = HoldingId("holding")
        val line = StatementLine(
            holdingId, owner, LocalDate.parse("2026-10-08"), 100_000_000L,
            MoneyScaled(100_000),
        )
        val plan = SnapshotReconciler.review(
            listOf(line),
            listOf(FinancialHolding(holdingId, owner, "Synthetic fund", "MUTUAL_FUND", MoneyScaled(100_000))),
            emptyList(), owner, "a".repeat(64),
        )
        val receipt = ReviewedStatement(
            "snapshot", owner, listOf(line), "b".repeat(64), plan, "c".repeat(64),
        )
        var action: StatementReviewIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(),
                    onIntent = {},
                    reviewState = StatementReviewUiState.Reviewing(receipt),
                    onReviewIntent = { action = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Confirm and authenticate approval").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, action) }
        // The review screen scrolls behind the bottom navigation. Scroll the
        // real checkbox into view BEFORE clicking so the navigation bar cannot
        // intercept an off-screen tap. Verify its semantic state changes.
        compose.onNodeWithContentDescription("Confirm statement changes")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
        compose.onNodeWithText("Confirm and authenticate approval")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(StatementReviewIntent.RequestApproval, action) }
    }

    @Test fun conflictedStatementCannotBeApproved() {
        val owner = OwnerId("self")
        val line = StatementLine(
            HoldingId("unknown"), owner, LocalDate.parse("2026-10-08"),
            100_000_000L, MoneyScaled(100_000),
        )
        val plan = SnapshotReconciler.review(
            listOf(line), emptyList(), emptyList(), owner, "d".repeat(64),
        )
        val receipt = ReviewedStatement(
            "conflict", owner, listOf(line), "e".repeat(64), plan, "f".repeat(64),
        )
        var action: StatementReviewIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    reviewState = StatementReviewUiState.Reviewing(receipt),
                    onReviewIntent = { action = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Approval blocked by conflicts").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, action) }
    }

}
