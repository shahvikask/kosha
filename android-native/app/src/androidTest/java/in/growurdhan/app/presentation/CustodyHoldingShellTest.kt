package `in`.growurdhan.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.growurdhan.app.presentation.theme.GrowUrDhanTheme
import `in`.growurdhan.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustodyHoldingShellTest {
    @get:Rule val compose = createComposeRule()
    private val owner = OwnerId("self")
    private val member = FamilyMember(owner, "Synthetic Primary", "Self", true)
    private val account = CustodyAccount("a1", owner, "CAMS", "Synthetic registrar")
    private fun book(accounts: List<CustodyAccount> = emptyList()): VaultBook =
        VaultBook(listOf(member), emptyList(), emptyList(), accounts)

    private val opened = VaultUiState.Ready(VaultHealth("ok", 0, 1, 0, 0))

    @Test fun vaultOpensSecondaryRegistrarEnrollmentNotAnotherBottomTab() {
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings()) {
                GrowUrDhanShell(
                    state = AppUiState(),
                    onIntent = {},
                    vaultState = opened,
                    custodyState = CustodyHoldingUiState.Ready(book()),
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Manage accounts & holdings").performScrollTo().performClick()
        compose.onNodeWithText("Accounts & holdings").assertIsDisplayed()
        compose.onNodeWithText("Save a CAMS or KFINTECH registrar account first.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun accountNeedsNicknameAndOnlyStagesUserRequestedWrite() {
        var action: CustodyHoldingIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings()) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {}, vaultState = opened,
                    custodyState = CustodyHoldingUiState.Ready(book()),
                    onCustodyIntent = { action = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Manage accounts & holdings").performScrollTo().performClick()
        compose.onNodeWithText("Save registrar account securely")
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Registrar account nickname input")
            .performScrollTo().performTextInput("Synthetic nickname")
        compose.onNodeWithText("Save registrar account securely")
            .performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                CustodyHoldingIntent.StageAccount(owner, "CAMS", "Synthetic nickname"), action,
            )
        }
    }

    @Test fun enrolledFundFormStagesExactIsinAndFolioWithoutLedgerMutation() {
        var action: CustodyHoldingIntent? = null
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings()) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {}, vaultState = opened,
                    custodyState = CustodyHoldingUiState.Ready(book(listOf(account))),
                    onCustodyIntent = { action = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Vault").performClick()
        compose.onNodeWithText("Manage accounts & holdings").performScrollTo().performClick()
        compose.onNodeWithText("Mutual fund scheme name")
            .performScrollTo().performTextInput("Synthetic Index")
        compose.onNodeWithContentDescription("CAS ISIN input")
            .performScrollTo().performTextInput("INF000000001")
        compose.onNodeWithContentDescription("CAS folio input")
            .performScrollTo().performTextInput("12345")
        compose.onNodeWithText("Enrol mutual fund securely").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                CustodyHoldingIntent.StageHolding(
                    owner, account.id, "Synthetic Index", "INF000000001", "12345",
                ), action,
            )
        }
    }

    @Test fun statementWithoutEnrolledIdentityOffersRealSetupRoute() {
        compose.setContent {
            GrowUrDhanTheme(AppearanceSettings()) {
                GrowUrDhanShell(
                    state = AppUiState(), onIntent = {},
                    reviewState = StatementReviewUiState.MissingCasMapping,
                    custodyState = CustodyHoldingUiState.Ready(book()),
                )
            }
        }
        compose.onNodeWithContentDescription("Statements").performClick()
        compose.onNodeWithText("Enrol your first CAS holding").assertIsDisplayed()
        compose.onNodeWithText("Add account and holding").performClick()
        compose.onNodeWithText("Accounts & holdings").assertIsDisplayed()
    }
}
