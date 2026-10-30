package `in`.growurdhan.model

/**
 * E18 fixed, non-personal diagnostics. No arbitrary name, text, URL, owner,
 * statement, amount, timestamp, financial balance or identifier is admitted.
 * Counts live in process RAM only; never automatically exported or uploaded.
 */
object NativePrivacySafeMetrics {
    enum class Event { COLD_INIT, OFFLINE_FETCH_FAILURE, PLAY_CATALOG_FAILURE, VAULT_LOCK }
    data class Counter(val event: Event, val count: Int, val lastLatencyBucketMs: Int?)
    private val counts = mutableMapOf<Event, Int>()
    private val buckets = mutableMapOf<Event, Int>()
    @Synchronized fun record(event: Event, elapsedMs: Long? = null) {
        require(elapsedMs == null || elapsedMs >= 0L)
        counts[event] = minOf(1_000_000, (counts[event] ?: 0) + 1)
        if (elapsedMs != null) {
            // Coarse bounded buckets, no per-customer duration history.
            val n = when {
                elapsedMs < 100L -> 100
                elapsedMs < 500L -> 500
                elapsedMs < 2_000L -> 2000
                else -> 10_000
            }
            buckets[event] = n
        }
    }
    @Synchronized fun snapshot(): List<Counter> = Event.entries.mapNotNull { event ->
        counts[event]?.let { Counter(event, it, buckets[event]) }
    }
    @Synchronized fun clear() { counts.clear(); buckets.clear() }
}
