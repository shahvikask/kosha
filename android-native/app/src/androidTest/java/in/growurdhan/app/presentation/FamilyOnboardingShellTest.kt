package `in`.growurdhan.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.core.model.ThemeMode
import `in`.growurdhan.core.model.VaultHealth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Consent to insert is never simulated by this UI test; it must reach the Activity credential gate. */
@RunWith(AndroidJUnit4::class)
class FamilyOnboardingShellTest {
    @get:Rule val compose = createComposeRule()
    private val appearance = AppearanceSettings(ThemeMode.LIGHT, dynamicColor = false)

    @Test fun statementsMissingOwnerProvidesDirectFamilySetupRoute() {
        var familyIntent: FamilyMemberIntent? = null
        compose.setContent {
            GrowUrDhanTheme(appearance) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    reviewState = StatementReviewUiState.MissingFamilyMember,
                    familyState = FamilyMemberUiState.Ready(emptyList()),
                    onFamilyIntent = { familyIntent = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Add a family member to continue").assertIsDisplayed()
        compose.onNodeWithText("Add family member").performScrollTo().performClick()
        compose.onNodeWithText("Add yourself as the primary owner").assertIsDisplayed()
        compose.runOnIdle { assertEquals(FamilyMemberIntent.Refresh, familyIntent) }
    }

    @Test fun firstFamilyMemberFormRequiresNameAndStagesOnly() {
        var familyIntent: FamilyMemberIntent? = null
        compose.setContent {
            GrowUrDhanTheme(appearance) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    reviewState = StatementReviewUiState.MissingFamilyMember,
                    familyState = FamilyMemberUiState.Ready(emptyList()),
                    onFamilyIntent = { familyIntent = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Add family member").performScrollTo().performClick()
        compose.onNodeWithText("Continue and authenticate")
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Family member name input")
            .performScrollTo().performTextInput("Synthetic Primary")
        compose.onNodeWithText("Continue and authenticate")
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(FamilyMemberIntent.Stage("Synthetic Primary", "Self"), familyIntent)
        }
    }

    @Test fun savedFamilyMemberCanReturnToStatementOwnerSelection() {
        val member = FamilyMember(OwnerId("id-primary"), "Synthetic Primary", "Self", true)
        var reviewIntent: StatementReviewIntent? = null
        compose.setContent {
            GrowUrDhanTheme(appearance) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    reviewState = StatementReviewUiState.MissingFamilyMember,
                    familyState = FamilyMemberUiState.Saved(member),
                    onReviewIntent = { reviewIntent = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Add family member").performScrollTo().performClick()
        compose.onNodeWithText("Family member saved").assertIsDisplayed()
        compose.onNodeWithText("Continue to Statements").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(StatementReviewIntent.LoadOwners, reviewIntent) }
        compose.onNodeWithText("Review a statement").assertIsDisplayed()
    }

    @Test fun unlockedEmptyVaultShowsFirstMemberAction() {
        compose.setContent {
            GrowUrDhanTheme(appearance) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    vaultState = VaultUiState.Ready(VaultHealth("ok", 0, 0, 0, 0)),
                    familyState = FamilyMemberUiState.Ready(emptyList()),
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Add first family member").performScrollTo().performClick()
        compose.onNodeWithText("Add yourself as the primary owner").assertIsDisplayed()
    }
}
