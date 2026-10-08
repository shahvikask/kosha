package `in`.growurdhan.app.presentation.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.growurdhan.app.presentation.AppIntent
import `in`.growurdhan.core.model.AppearanceSettings
import `in`.growurdhan.core.model.ThemeMode

@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    onIntent: (AppIntent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Appearance",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Choose how GrowUrDhan looks. Changes are kept on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ThemeMode.entries.forEach { option ->
                    val selected = appearance.theme == option
                    val label = when (option) {
                        ThemeMode.SYSTEM -> "Follow device"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIntent(AppIntent.SelectTheme(option))
                            }
                            .semantics { role = Role.RadioButton }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic color", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (dynamicAvailable) "Use your device wallpaper colors."
                        else "Available on Android 12 and newer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = appearance.dynamicColor && dynamicAvailable,
                    enabled = dynamicAvailable,
                    onCheckedChange = { enabled ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onIntent(AppIntent.SetDynamicColor(enabled))
                    },
                )
            }
        }
    }
}
