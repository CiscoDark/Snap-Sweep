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
import com.example.util.InstallationHelper
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

            // Safety Check 1: Auto-Sweep must be explicitly enabled by user
            if (stats?.autoSweepEnabled != true) {
                return@withContext Result.success()
            }

            // Safety Check 2: Absolute cutoff at app install timestamp
            val installTimestamp = InstallationHelper.getOrSetInstallTimestamp(applicationContext)

            val defaultExpirationMinutes = stats.defaultExpirationMinutes
            val defaultDurationMillis = defaultExpirationMinutes * 60 * 1000L
            val nowMillis = System.currentTimeMillis()

            // Fetch all stored screenshot records from Room (kept, trashed, snoozed)
            val dbRecords = dao.getAllScreenshotStates().firstOrNull().orEmpty()
            val dbMap = dbRecords.associateBy { it.id }

            if (MediaStoreScanner.hasReadImagesPermission(applicationContext)) {
                val realScreenshots = MediaStoreScanner.scanScreenshots(applicationContext, installTimestamp)

                for (screenshot in realScreenshots) {
                    // Absolute Safety Cutoff: NEVER touch screenshots taken prior to install
                    if (screenshot.dateAddedMillis < installTimestamp || screenshot.isLegacy) {
                        continue
                    }

                    val dbRecord = dbMap[screenshot.id]
                    // Skip if already Kept or already in Recycle Bin
                    if (dbRecord?.isKept == true || dbRecord?.isTrashed == true) {
                        continue
                    }

                    val effectiveExpiration = dbRecord?.snoozeUntilMillis
                        ?: (screenshot.dateAddedMillis + defaultDurationMillis)

                    if (effectiveExpiration <= nowMillis) {
                        // Expired post-install screenshot! Move to Recycle Bin (Trash)
                        // NEVER call permanent delete - files are kept recoverable in Recycle Bin
                        dao.updateTrashedStatus(screenshot.id, isTrashed = true, trashedAt = nowMillis)
                        dao.recordDeletedItem(screenshot.sizeBytes)
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

        fun cancelPeriodic(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
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
