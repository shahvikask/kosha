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
import `in`.growurdhan.designsystem.GUDLockedBanner
import `in`.growurdhan.designsystem.GUDNetWorthHero
import `in`.growurdhan.designsystem.GUDUnknownCostNotice
import `in`.growurdhan.designsystem.GudTheme
import `in`.growurdhan.model.ConceptLock
import `in`.growurdhan.model.ConceptPlace
import `in`.growurdhan.model.InrFormat

private const val SAMPLE_MINOR = 12_345_678_900L

@Composable
fun ConceptApp() {
    var place by rememberSaveable { mutableStateOf(ConceptPlace.Locked) }
    val lock = ConceptLock(place)
    when (lock.place) {
        ConceptPlace.Locked -> UnlockScreen(onViewSample = { place = lock.viewSampleLayout().place })
        ConceptPlace.SampleHome -> HomeScreen(
            onOpenHolding = { place = lock.viewSampleHolding().place },
            onReturnToLocked = { place = lock.returnToLocked().place },
        )
        ConceptPlace.SampleHolding -> HoldingDetailScreen(
            onBack = { place = lock.backToSampleHome().place },
            onReturnToLocked = { place = lock.returnToLocked().place },
        )
    }
}

@Composable
fun UnlockScreen(onViewSample: () -> Unit) {
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
            Spacer(Modifier.height(12.dp))
            GUDLockedBanner()
            Text(
                "There is no password field. This build has no vault to open.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onViewSample, modifier = Modifier.height(48.dp)) {
                Text("View sample layout")
            }
        }
    }
}

@Composable
fun HomeScreen(onOpenHolding: () -> Unit, onReturnToLocked: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            GUDLockedBanner()
            Spacer(Modifier.height(16.dp))
            GUDBrandLockup()
            Spacer(Modifier.height(16.dp))
            GUDNetWorthHero(InrFormat.formatOverview(SAMPLE_MINOR))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenHolding, modifier = Modifier.height(48.dp)) {
                Text("View sample holding")
            }
            TextButton(onClick = onReturnToLocked, modifier = Modifier.height(48.dp)) {
                Text("Return to locked screen")
            }
        }
    }
}

@Composable
fun HoldingDetailScreen(onBack: () -> Unit, onReturnToLocked: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            GUDLockedBanner()
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
            TextButton(onClick = onReturnToLocked, modifier = Modifier.height(48.dp)) {
                Text("Return to locked screen")
            }
        }
    }
}

@Preview(name = "Unlock light")
@Composable
private fun UnlockLightPreview() {
    GudTheme(darkTheme = false) { UnlockScreen(onViewSample = {}) }
}

@Preview(name = "Home dark")
@Composable
private fun HomeDarkPreview() {
    GudTheme(darkTheme = true) { HomeScreen(onOpenHolding = {}, onReturnToLocked = {}) }
}

@Preview(name = "Holding light")
@Composable
private fun HoldingLightPreview() {
    GudTheme(darkTheme = false) { HoldingDetailScreen(onBack = {}, onReturnToLocked = {}) }
}
