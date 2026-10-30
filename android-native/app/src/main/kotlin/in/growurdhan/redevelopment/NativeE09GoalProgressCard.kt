package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativeE09Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** E09 same authenticated owner lens as Goals, no cached plaintext financial data. */
@Composable
internal fun NativeE09GoalProgressCard(email: String, books: NativeBooksRepository,
    memberId: String, holdings: List<NativeBooksRepository.Holding>) {
    var state by remember(email, memberId) {
        mutableStateOf<List<NativeE09Analytics.GoalProgress>?>(null)
    }
    var failed by remember(email, memberId) { mutableStateOf(false) }
    LaunchedEffect(email, memberId, holdings) {
        state = null
        failed = false
        try {
            state = withContext(Dispatchers.IO) {
                books.recordedGoalProgress(email, memberId)
            }
        } catch (_: Exception) { failed = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recorded goal progress", style = MaterialTheme.typography.titleLarge)
        Text("Only explicitly linked current holdings with verified dated recorded " +
            "prices count; unknown or partial valuations never become zero.")
        when {
            failed -> Text("Goal progress unavailable. Unlock and reconcile your books.",
                modifier = Modifier.testTag("e09-goal-error"))
            state == null -> Text("Loading encrypted goal evidence…")
            state!!.isEmpty() -> Text("No goals in the selected owner view.",
                modifier = Modifier.testTag("e09-goal-empty"))
            else -> state!!.forEach { goal ->
                Text(goal.name + ": " + (goal.basisPoints?.let {
                    "$it / 10,000 basis points of recorded target"
                } ?: "N/A — incomplete or unpriced linked holdings") +
                    " · linked ${goal.linkedHoldingCount}, excluded ${goal.excludedLinkedHoldings}" +
                    (goal.completeValuedMinor?.let {
                        " · dated value ₹" + BigDecimal(it, 4).toPlainString()
                    } ?: ""),
                    modifier = Modifier.testTag("e09-goal-${goal.id}"))
            }
        }
    }
}
