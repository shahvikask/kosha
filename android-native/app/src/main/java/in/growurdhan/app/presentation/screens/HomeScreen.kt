package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import `in`.growurdhan.app.presentation.HouseholdDashboardUiState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Honest empty state: M1 does not create a financial vault, import fabricated
 * holdings, or read/mutate the existing Tauri database.
 */
@Composable
fun HomeScreen(
    dashboardState: HouseholdDashboardUiState = HouseholdDashboardUiState.Locked,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenVault: () -> Unit = {},
    onOpenStatements: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Family wealth, with clarity.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Private by design. Your financial books remain on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Your financial overview", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(when (dashboardState) {
                    is HouseholdDashboardUiState.Ready -> "Recorded net worth: " + dashboardState.dashboard.netWorth.rupees().toPlainString() + " INR; recorded assets: " + dashboardState.dashboard.assets.rupees().toPlainString() + " INR; recorded liabilities: " + dashboardState.dashboard.liabilities.rupees().toPlainString() + " INR. Values use stored unit prices, not live market quotes. " + dashboardState.dashboard.memberCount + " family members; " + dashboardState.dashboard.holdingCount + " holdings." + (if (dashboardState.dashboard.unknownCostHoldings > 0) " Historical cost unknown for " + dashboardState.dashboard.unknownCostHoldings + " holdings." else "") + (if (!dashboardState.dashboard.isReliable) " Financial integrity review required; totals may be incomplete." else "")
                    HouseholdDashboardUiState.Loading -> "Calculating your household overview."
                    HouseholdDashboardUiState.Unavailable -> "Overview unavailable. Please retry after unlocking."
                    HouseholdDashboardUiState.Locked -> "Unlock the vault to view your family wealth."
                },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick = onOpenVault) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                    Text("Open vault", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenStatements, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Text("Statements", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Text("Privacy", modifier = Modifier.padding(start = 6.dp))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                    Text(
                        "Portfolio insights are being prepared",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "The native vault opens only after device authentication and does not " +
                        "migrate your existing React/Tauri book. Set up a primary family member " +
                        "before importing a CAS PDF. Verified PDF parsing is restricted to supported " +
                        "ARM64 release builds and requires existing holding mappings. Full portfolio " +
                        "and onboarding parity remain in development.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenSettings) {
                Text("Personalize")
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOpenPrivacy) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Privacy")
            }
        }
    }
}
