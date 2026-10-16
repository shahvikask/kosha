package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.designsystem.GUDNetWorthHero
import `in`.growurdhan.designsystem.GUDUnknownCostNotice
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.InrFormat

private const val SAMPLE_MINOR = 12_345_678_900L

private enum class ConceptRoute { Unlock, Home, Holding }

@Composable
fun ConceptApp() {
    var route by rememberSaveable { mutableStateOf(ConceptRoute.Unlock) }
    when (route) {
        ConceptRoute.Unlock -> UnlockScreen(onPreview = { route = ConceptRoute.Home })
        ConceptRoute.Home -> HomeScreen(onOpenHolding = { route = ConceptRoute.Holding })
        ConceptRoute.Holding -> HoldingDetailScreen(onBack = { route = ConceptRoute.Home })
    }
}

@Composable
fun UnlockScreen(onPreview: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(32.dp))
            GUDBrandLockup()
            Spacer(Modifier.height(28.dp))
            Text("Locked", style = MaterialTheme.typography.headlineMedium)
            Text(
                "No vault is opened. This screen does not check a password or read a book.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPreview, modifier = Modifier.height(48.dp)) {
                Text("Preview sample home")
            }
        }
    }
}

@Composable
fun HomeScreen(onOpenHolding: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            GUDBrandLockup()
            Spacer(Modifier.height(16.dp))
            GUDNetWorthHero(InrFormat.formatOverview(SAMPLE_MINOR))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenHolding, modifier = Modifier.height(48.dp)) {
                Text("Open sample holding")
            }
        }
    }
}

@Composable
fun HoldingDetailScreen(onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text("Back to sample home")
            }
            Text("Sample listed equity", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Shown only to exercise the holding-detail layout.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            GUDUnknownCostNotice()
        }
    }
}

@Preview(name = "Unlock light")
@Composable
private fun UnlockLightPreview() {
    GudTheme(darkTheme = false) { UnlockScreen(onPreview = {}) }
}

@Preview(name = "Home dark")
@Composable
private fun HomeDarkPreview() {
    GudTheme(darkTheme = true) { HomeScreen(onOpenHolding = {}) }
}

@Preview(name = "Holding light")
@Composable
private fun HoldingLightPreview() {
    GudTheme(darkTheme = false) { HoldingDetailScreen(onBack = {}) }
}
