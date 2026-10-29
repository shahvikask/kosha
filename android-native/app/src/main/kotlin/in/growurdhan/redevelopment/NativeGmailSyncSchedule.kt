package `in`.growurdhan.redevelopment

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Scheduler stores no vault email, OAuth token or statement bytes in WorkManager. */
internal object NativeGmailSyncSchedule {
    internal const val PERIODIC = "native-gmail-monthly"
    internal const val MANUAL = "native-gmail-manual"
    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Call only after authenticated explicit owner consent and mapping checks. */
    fun enableMonthly(context: Context) {
        val request = PeriodicWorkRequestBuilder<NativeGmailSyncWorker>(
            30, TimeUnit.DAYS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** No automatic scheduling on app startup; this is explicit user action. */
    fun requestManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<NativeGmailSyncWorker>()
            .setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL, ExistingWorkPolicy.KEEP, request)
    }

    fun revoke(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL)
    }
}

/**
 * Deliberately fails closed: background execution cannot unlock the owner vault,
 * fetch Gmail, or move cursors without a future authenticated provider adapter.
 */
internal class NativeGmailSyncWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {
    override fun doWork(): Result = Result.failure()
}
