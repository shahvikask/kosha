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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Your wealth, on your device.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "A quieter, clearer way to understand family wealth.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                    Text(
                        "Native portfolio screens still in development",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "The native encrypted vault can be created only after a deliberate, " +
                        "device-authenticated action. It does not migrate or open your existing " +
                        "React/Tauri financial book. The full portfolio and verified statement " +
                        "PDF import screens are not yet connected.",
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
