package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import com.example.data.scanner.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.screenshotDao()
            val stats = dao.getAppStats().firstOrNull()

            val defaultExpirationMinutes = stats?.defaultExpirationMinutes ?: 1440L
            val defaultDurationMillis = defaultExpirationMinutes * 60 * 1000L
            val nowMillis = System.currentTimeMillis()

            // Fetch all kept/snoozed screenshot records from Room
            val dbRecords = dao.getAllScreenshotStates().firstOrNull().orEmpty()
            val dbMap = dbRecords.associateBy { it.id }

            // If real media scan permission is granted
            if (MediaStoreScanner.hasReadImagesPermission(applicationContext)) {
                val realScreenshots = MediaStoreScanner.scanScreenshots(applicationContext)

                for (screenshot in realScreenshots) {
                    val dbRecord = dbMap[screenshot.id]
                    val isKept = dbRecord?.isKept == true
                    if (isKept) {
                        // Skip kept/protected screenshots
                        continue
                    }

                    val effectiveExpiration = dbRecord?.snoozeUntilMillis
                        ?: (screenshot.dateAddedMillis + defaultDurationMillis)

                    if (effectiveExpiration <= nowMillis) {
                        // Expired item! Delete if manage storage permission is granted
                        val uri = screenshot.contentUri
                        if (uri != null && MediaStoreScanner.hasManageStoragePermission()) {
                            try {
                                val deleted = applicationContext.contentResolver.delete(uri, null, null)
                                if (deleted > 0) {
                                    dao.recordDeletedItem(screenshot.sizeBytes)
                                    dao.deleteById(screenshot.id)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "snapsweep_periodic_cleanup"
        const val ONE_TIME_WORK_NAME = "snapsweep_manual_cleanup"

        fun schedulePeriodic(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun triggerImmediate(context: Context) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<CleanupWorker>().build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
