package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fictional IDs only. Real lazy Compose scrolling with full append-only lineage. */
@RunWith(AndroidJUnit4::class)
class NativeM27LazyAuditHistoryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun rows(count: Int) = (0 until count).map { idx ->
        val id = "fictional-event-${idx.toString().padStart(4, '0')}"
        NativeBooksRepository.EventRow(
            id, "fictional-holding", "fictional-owner",
            if (idx == count - 1) "CORRECTION" else "BUY",
            "2025-02-01", 100_000_000L, 20_000L, 200_000L, 1_000L,
            if (idx == count - 1) "fictional-event-0001" else null,
            "Synthetic audit evidence")
    }

    @Test fun longHistoryOnlyComposesVisibleRowsAndScrollsToLastFullCorrectionId() {
        compose.setContent {
            MaterialTheme { NativeHoldingAuditHistory(rows(225)) }
        }
        compose.onNodeWithTag("m27-history-list").assertExists()
        compose.onNodeWithTag("m27-history-fictional-event-0000").assertExists()
        compose.onNodeWithTag("m27-history-fictional-event-0224").assertDoesNotExist()
        compose.onNodeWithTag("m27-history-list").performScrollToIndex(224)
        compose.onNodeWithTag("m27-history-fictional-event-0224")
            .assertExists().assertTextContains("corrects full event ID fictional-event-0001", substring = true)
    }

    @Test fun sourcedSnapshotShowsReadOnlyReconciliationAtHighFontScale() {
        val e = NativeBooksRepository.EventRow("fictional-snapshot",
            "fictional-holding", "fictional-owner", "SNAPSHOT_ADJUSTMENT",
            "2025-02-01", 0L, 0L, 0L, 0L, null, null, sourceBacked = true)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(360.dp)) {
                        NativeHoldingAuditHistory(listOf(e))
                    }
                }
            }
        }
        compose.onNodeWithTag("m27-source-event-fictional-snapshot")
            .assertExists().assertTextContains("Source-referenced", substring = true)
        compose.onNodeWithTag("m27-source-snapshot-fictional-snapshot")
            .assertExists().assertTextContains("read-only reconciliation", substring = true)
    }

    @Test fun lastAuditIdAndFullCorrectionRemainAccessibleAt360dpAnd200PercentText() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val scale = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(scale.density, 2f)
                ) {
                    Box(Modifier.width(360.dp)) {
                        NativeHoldingAuditHistory(rows(120))
                    }
                }
            }
        }
        compose.onNodeWithTag("m27-history-list").performScrollToIndex(119)
        compose.onNodeWithTag("m27-history-fictional-event-0119")
            .assertExists().assertTextContains("Event ID fictional-event-0119", substring = true)
            .assertTextContains("corrects full event ID fictional-event-0001", substring = true)
    }
}
