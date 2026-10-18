package `in`.growurdhan.model

/**
 * Only a foreground activity that launched an explicit OS challenge can accept its result.
 * Backgrounding always revokes admission; the in-flight challenge remains pending so a
 * legitimately returning Android credential activity may complete after onStart().
 * The result is not a cryptographic authentication token and must not replace vault proof.
 */
class NativeDeviceAdmission {
    private var foreground = false
    private var pending = false
    private var admitted = false
    private var generation = 0L
    @Synchronized fun onStart() { foreground = true }
    @Synchronized fun onStop() { foreground = false; admitted = false; generation++ }
    @Synchronized fun begin(): Boolean {
        admitted = false
        generation++
        if (!foreground) return false
        pending = true
        return true
    }
    @Synchronized fun complete(success: Boolean): Boolean {
        admitted = foreground && pending && success
        generation++
        pending = false
        return admitted
    }
    @Synchronized fun valid(): Boolean = foreground && admitted
    /** Tickets are ephemeral generation identifiers, not OS credentials. */
    @Synchronized fun ticket(): Long? = if (valid()) generation else null
    @Synchronized fun validTicket(value: Long): Boolean =
        foreground && admitted && generation == value
    @Synchronized fun cancel() { pending = false; admitted = false; generation++ }
}