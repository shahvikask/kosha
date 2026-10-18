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
    fun onStart() { foreground = true }
    fun onStop() { foreground = false; admitted = false }
    fun begin(): Boolean {
        admitted = false
        if (!foreground) return false
        pending = true
        return true
    }
    fun complete(success: Boolean): Boolean {
        admitted = foreground && pending && success
        pending = false
        return admitted
    }
    fun valid(): Boolean = foreground && admitted
    fun cancel() { pending = false; admitted = false }
}