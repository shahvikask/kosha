package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativeCommercialAccessController
import `in`.growurdhan.model.NativeCommercialEntitlements

/**
 * P37/E16 noninteractive, truthful store status. This card grants nothing:
 * the commercial release must wire a trusted Play verifier before enforcement.
 */
@Composable
internal fun NativeBillingStatusNotice() {
    val access = NativeCommercialAccessController().current(System.currentTimeMillis())
    val context = LocalContext.current
    var probing by remember { mutableStateOf(false) }
    var probeStatus by remember { mutableStateOf("No Play catalog check requested.") }
    var pending by remember { mutableStateOf<NativePlayCatalogProbe?>(null) }
    DisposableEffect(Unit) { onDispose { pending?.close() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Play entitlements", style = MaterialTheme.typography.titleLarge)
        Text("Google Play Billing is not connected in this build. No purchase, " +
            "paid entitlement or Play-verified trial can be confirmed. " +
            "The licence gate is not yet integrated with app navigation.",
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("p37-play-unconfigured"))
        Text("Recognized product identifiers: " +
            listOf(NativeCommercialEntitlements.MANUAL, NativeCommercialEntitlements.GMAIL,
                NativeCommercialEntitlements.UPGRADE,
                NativeCommercialEntitlements.SUPPORT).joinToString(", ") +
            ". Pricing and purchase buttons require verified Play configuration.")
        OutlinedButton(enabled = !probing, onClick = {
            probing = true
            probeStatus = "Reading available products from Play. No purchase is performed."
            val probe = NativePlayCatalogProbe(context)
            pending = probe
            probe.readCatalog { result ->
                probing = false
                pending = null
                probeStatus = if (result.unavailable)
                    "Google Play catalog unavailable; no purchase or entitlement can be confirmed."
                else "Play catalog: ${result.report?.recognized ?: 0} of four known " +
                    "identifiers matched. Missing or mismatched products are not purchasable " +
                    "here. Catalog discovery does not verify any purchase."
            }
        }, modifier = Modifier.heightIn(min = 48.dp)
            .testTag("p37-check-play-catalog")) {
            Text(if (probing) "Checking Play catalog…" else "Check Play product catalog")
        }
        Text(probeStatus, modifier = Modifier.testTag("p37-play-catalog-status"))
        if (!access.canRead) {
            Text("Verification status: unavailable (no trusted Play response).",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
