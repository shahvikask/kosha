package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Source policy only: no RBI/IBJA prices are fetched, assumed or stored. */
@Composable
internal fun NativeUserSettingsCard(
    preferences: NativeUserSettingsRepository.Preferences,
    enabled: Boolean,
    onChange: (NativeUserSettingsRepository.Preferences) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        Text("Theme changes after encrypted save. System follows the device.")
        for (option in NativeUserSettingsRepository.Appearance.entries) {
            OutlinedButton(
                onClick = { onChange(preferences.copy(appearance = option)) },
                enabled = enabled && preferences.appearance != option,
                modifier = Modifier.heightIn(min = 48.dp).testTag("m31-theme-" + option.name)
            ) {
                Text(option.name.lowercase().replaceFirstChar { it.uppercase() } +
                    if (preferences.appearance == option) " selected" else "")
            }
        }
        Text("Prices & provenance", style = MaterialTheme.typography.titleLarge)
        Text("SGB reference policy (never edits recorded prices or ledger events)")
        for (option in NativeUserSettingsRepository.SgbReference.entries) {
            OutlinedButton(
                onClick = { onChange(preferences.copy(sgbReference = option)) },
                enabled = enabled && preferences.sgbReference != option,
                modifier = Modifier.heightIn(min = 48.dp).testTag("m31-sgb-" + option.name)
            ) {
                Text((if (option == NativeUserSettingsRepository.SgbReference.RECORDED_MARKET_CLOSE)
                    "Show recorded market close" else "Require verified RBI reference") +
                    if (preferences.sgbReference == option) " selected" else "")
            }
        }
        Text("No RBI redemption reference is verified in this release. Choosing " +
            "RBI mode hides unsupported SGB value previews rather than inventing prices.")
        Text("IBJA input policy (gold only; no automatic IBJA rates)")
        for (option in NativeUserSettingsRepository.IbjaReference.entries) {
            OutlinedButton(
                onClick = { onChange(preferences.copy(ibjaReference = option)) },
                enabled = enabled && preferences.ibjaReference != option,
                modifier = Modifier.heightIn(min = 48.dp).testTag("m31-ibja-" + option.name)
            ) {
                Text(if (option == NativeUserSettingsRepository.IbjaReference.DISABLED)
                    "IBJA disabled" else "Require owner review of IBJA input")
            }
        }
        Text("IBJA feed is not verified. No rate is stored or applied automatically; " +
            "any owner-entered prices are separately marked manual.")
        Text("Security & login", style = MaterialTheme.typography.titleLarge)
        Text("The vault is offline and password/device-gated. Account and PAN " +
            "references remain masked, with no unmasking control.")
        Text("Losing this device or uninstalling can permanently erase your financial data; " +
            "financial backup and cross-device restore are unavailable in this release.",
            modifier = Modifier.testTag("m31-device-loss-warning"))
    }
}
