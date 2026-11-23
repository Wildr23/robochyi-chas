package ua.robochyichas.app.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.robochyichas.app.WorkTimeApp
import java.util.concurrent.TimeUnit

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WorkTimeApp ?: return Result.failure()
        val syncResult = app.repository.runCloudSync(ignoreWifiOnly = false)
        return when (syncResult) {
            is SyncRunResult.Success -> Result.success()
            is SyncRunResult.AuthRequired -> Result.success() // Wait for user interactive re-auth
            is SyncRunResult.OfflineQueued,
            is SyncRunResult.RetryableError -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "robochyi_chas_drive_sync"

        fun enqueue(context: Context, wifiOnly: Boolean) {
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
