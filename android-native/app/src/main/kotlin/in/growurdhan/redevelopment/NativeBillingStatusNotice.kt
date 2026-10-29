package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        if (!access.canRead) {
            Text("Verification status: unavailable (no trusted Play response).",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
