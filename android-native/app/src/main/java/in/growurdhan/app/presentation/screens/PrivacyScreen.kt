package `in`.growurdhan.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Privacy by construction",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Native financial records can be held in an encrypted on-device SQLCipher vault. " +
                "This development build has no Gmail or analytics integration.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrivacyFact(
            title = "Offline foundation",
            detail = "The current native screens need no network connection.",
            icon = { Icon(Icons.Outlined.CloudOff, contentDescription = null) },
        )
        PrivacyFact(
            title = "Preferences on this device",
            detail = "Your display theme is stored locally using Android DataStore.",
            icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
        )
        PrivacyFact(
            title = "Encrypted vault and device credential",
            detail = "A configured Android screen lock is required before opening the native vault " +
                "or approving a reviewed statement. A GrowUrDhan-specific login is not yet implemented.",
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        )
    }
}

@Composable
private fun PrivacyFact(
    title: String,
    detail: String,
    icon: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
