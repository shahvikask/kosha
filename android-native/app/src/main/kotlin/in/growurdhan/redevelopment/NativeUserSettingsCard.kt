package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    ibja: NativeUserSettingsRepository.IbjaInput?,
    onRecordIbja: (String, String) -> Unit,
    onClearIbja: () -> Unit,
    onLock: () -> Unit,
    onChange: (NativeUserSettingsRepository.Preferences) -> Unit
) {
    var ibjaDate by remember { mutableStateOf("") }
    var ibjaRate by remember { mutableStateOf("") }
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
        Text("No verified IBJA feed is connected. Owner-entered per-10g 999-gold " +
            "references are informational, never a market quote or holding price.")
        if (preferences.ibjaReference ==
            NativeUserSettingsRepository.IbjaReference.OWNER_REVIEW_REQUIRED) {
            if (ibja == null) Text("No owner-entered IBJA reference stored.",
                modifier = Modifier.testTag("m31-ibja-empty"))
            else {
                Text("Unverified owner-entered IBJA reference ${ibja.asOf}: " +
                    "₹${ibja.displayRate()} / 10g 999 gold. No automatic valuation.",
                    modifier = Modifier.testTag("m31-ibja-record"))
                OutlinedButton(enabled = enabled, onClick = onClearIbja,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("m31-ibja-delete")) {
                    Text("Delete owner-entered IBJA reference")
                }
            }
            OutlinedTextField(value = ibjaDate, onValueChange = { ibjaDate = it.take(10) },
                label = { Text("Reference date YYYY-MM-DD") }, singleLine = true,
                enabled = enabled, modifier = Modifier.testTag("m31-ibja-date"))
            OutlinedTextField(value = ibjaRate, onValueChange = { ibjaRate = it.take(16) },
                label = { Text("Owner-entered ₹ per 10g, 999 gold") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled, modifier = Modifier.testTag("m31-ibja-rate"))
            OutlinedButton(enabled = enabled && ibjaDate.isNotBlank() &&
                ibjaRate.isNotBlank(), onClick = { onRecordIbja(ibjaDate, ibjaRate) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("m31-ibja-save")) {
                Text("Save unverified manual IBJA reference")
            }
        }
        Text("Security & login", style = MaterialTheme.typography.titleLarge)
        Text("The vault is offline and password/device-gated. Account and PAN " +
            "references remain masked, with no unmasking control.")
        Text("Lock the vault to use password change or recovery in the verified " +
            "account authentication screen.")
        OutlinedButton(enabled = enabled, onClick = onLock,
            modifier = Modifier.heightIn(min = 48.dp).testTag("m31-account-lock")) {
            Text("Lock vault now")
        }
        Text("Losing this device or uninstalling can permanently erase your financial data; " +
            "financial backup and cross-device restore are unavailable in this release.",
            modifier = Modifier.testTag("m31-device-loss-warning"))
    }
}
