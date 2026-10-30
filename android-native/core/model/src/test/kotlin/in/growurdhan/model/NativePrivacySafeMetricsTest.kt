package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativePrivacySafeMetricsTest {
    @Test fun emitsOnlyFixedNamesAndBoundedAggregateBuckets() {
        NativePrivacySafeMetrics.clear()
        NativePrivacySafeMetrics.record(NativePrivacySafeMetrics.Event.COLD_INIT, 67)
        NativePrivacySafeMetrics.record(NativePrivacySafeMetrics.Event.COLD_INIT, 140)
        val row = NativePrivacySafeMetrics.snapshot().single()
        assertEquals(2, row.count)
        assertEquals(500, row.lastLatencyBucketMs)
        assertEquals("COLD_INIT", row.event.name)
        assertThrows(IllegalArgumentException::class.java) {
            NativePrivacySafeMetrics.record(NativePrivacySafeMetrics.Event.VAULT_LOCK, -1)
        }
        NativePrivacySafeMetrics.clear()
        assertTrue(NativePrivacySafeMetrics.snapshot().isEmpty())
    }
}
