package `in`.growurdhan.redevelopment

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Import batch / source_document has no verified per-member owner column.
 * Do not pretend that the current family lens can filter these records.
 * Household-only access remains possible after explicit lens switch.
 */
@Composable
internal fun NativeHouseholdDocumentAccessGate(
    selectedMemberId: String,
    content: @Composable () -> Unit
) {
    if (selectedMemberId.isNotEmpty()) {
        Text("Statement imports and document history span the whole household. " +
            "Switch to Whole household to import or review original sources.")
    } else {
        content()
    }
}
