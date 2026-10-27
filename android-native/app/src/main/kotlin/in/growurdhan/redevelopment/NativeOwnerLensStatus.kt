package `in`.growurdhan.redevelopment

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * A family lens filters the displayed records; it is not authentication.
 * Announce scope changes without revealing PANs or account identifiers.
 */
@Composable
internal fun NativeOwnerLensStatus(memberName: String?) {
    val scope = if (memberName == null) "Whole household" else "Member: $memberName"
    Text(
        text = "Viewing $scope financial records",
        style = MaterialTheme.typography.bodyMedium,
        // Isolated composables must not inherit an unthemed black LocalContentColor.
        // This also keeps the actual dark-mode owner scope legible.
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("owner-lens-status")
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}
