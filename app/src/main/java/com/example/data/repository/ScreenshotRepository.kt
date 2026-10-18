package com.example.data.repository

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.AppDatabase
import com.example.data.db.AppStatsEntity
import com.example.data.db.KeptScreenshotEntity
import com.example.data.model.ScreenshotItem
import com.example.data.scanner.MediaStoreScanner
import com.example.util.InstallationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class ScreenshotRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val dao = database.screenshotDao()

    // Real MediaStore scanned items
    private val _realScreenshots = MutableStateFlow<List<ScreenshotItem>>(emptyList())
    val realScreenshots = _realScreenshots.asStateFlow()

    fun getAppStats(): Flow<AppStatsEntity?> = dao.getAppStats()

    fun getKeptScreenshots(): Flow<List<KeptScreenshotEntity>> = dao.getKeptScreenshots()

    fun getTrashedScreenshots(): Flow<List<KeptScreenshotEntity>> = dao.getTrashedScreenshots()

    fun getAllScreenshotDbRecords(): Flow<List<KeptScreenshotEntity>> = dao.getAllScreenshotStates()

    fun getInstallTimestamp(): Long = InstallationHelper.getOrSetInstallTimestamp(context)

    suspend fun initializeStatsIfEmpty() {
        val stats = dao.getAppStats().firstOrNull()
        val installTs = getInstallTimestamp()
        if (stats == null) {
            dao.insertOrUpdateStats(
                AppStatsEntity(
                    id = 1,
                    totalDeletedCount = 0,
                    totalBytesFreed = 0L,
                    defaultExpirationMinutes = 1440L, // 24 hours
                    themeMode = "SYSTEM",
                    pureBlackAmoled = false,
                    autoSweepEnabled = false, // DISABLED BY DEFAULT on install
                    installTimestamp = installTs
                )
            )
        } else if (stats.installTimestamp == 0L) {
            dao.updateInstallTimestamp(installTs)
        }
    }

    suspend fun setAutoSweepEnabled(enabled: Boolean) {
        dao.updateAutoSweepEnabled(enabled)
    }

    suspend fun setDefaultExpiration(minutes: Long) {
        dao.updateDefaultExpiration(minutes)
    }

    suspend fun updateThemeMode(mode: String) {
        dao.updateThemeMode(mode)
    }

    suspend fun updatePureBlackAmoled(enabled: Boolean) {
        dao.updatePureBlackAmoled(enabled)
    }

    suspend fun queryRealScreenshots(): List<ScreenshotItem> {
        if (MediaStoreScanner.hasReadImagesPermission(context)) {
            val installTs = getInstallTimestamp()
            val scanned = MediaStoreScanner.scanScreenshots(context, installTs)
            _realScreenshots.value = scanned
            return scanned
        }
        return emptyList()
    }

    suspend fun scanRealMedia() {
        if (MediaStoreScanner.hasReadImagesPermission(context)) {
            val installTs = getInstallTimestamp()
            val scanned = MediaStoreScanner.scanScreenshots(context, installTs)
            _realScreenshots.value = scanned
        }
    }

    suspend fun markAsKept(item: ScreenshotItem, category: String = item.category) {
        val finalCategory = if (category.isNotBlank() && category != "General") category else "Receipts"
        val entity = KeptScreenshotEntity(
            id = item.id,
            uriString = item.contentUri?.toString() ?: "",
            displayName = item.displayName,
            dateAddedMillis = item.dateAddedMillis,
            sizeBytes = item.sizeBytes,
            isKept = true,
            keptAtMillis = System.currentTimeMillis(),
            snoozeUntilMillis = item.snoozeUntilMillis,
            category = finalCategory,
            isTrashed = false,
            trashedAtMillis = null
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun updateCategory(id: String, category: String) {
        dao.updateCategory(id, category)
    }

    suspend fun unkeepToQueue(id: String) {
        dao.updateKeptStatus(id, isKept = false, keptAt = 0L)
    }

    suspend fun snoozeItem(item: ScreenshotItem, extraHours: Long = 24L) {
        val currentExpiry = item.getExpirationMillis(1440L * 60 * 1000L)
        val now = System.currentTimeMillis()
        val baseTime = maxOf(now, currentExpiry)
        val newSnoozeTime = baseTime + (extraHours * 3600 * 1000L)

        val existing = dao.getScreenshotById(item.id)
        if (existing != null) {
            dao.updateSnooze(item.id, newSnoozeTime)
        } else {
            dao.insertOrUpdate(
                KeptScreenshotEntity(
                    id = item.id,
                    uriString = item.contentUri?.toString() ?: "",
                    displayName = item.displayName,
                    dateAddedMillis = item.dateAddedMillis,
                    sizeBytes = item.sizeBytes,
                    isKept = false,
                    snoozeUntilMillis = newSnoozeTime,
                    category = item.category,
                    isTrashed = false
                )
            )
        }
    }

    suspend fun setOtpBurner(id: String, isOtpBurner: Boolean) {
        val existing = dao.getScreenshotById(id)
        if (existing != null) {
            dao.updateOtpBurner(id, isOtpBurner)
        } else {
            val item = _realScreenshots.value.find { it.id == id }
            if (item != null) {
                dao.insertOrUpdate(
                    KeptScreenshotEntity(
                        id = item.id,
                        uriString = item.contentUri?.toString() ?: "",
                        displayName = item.displayName,
                        dateAddedMillis = item.dateAddedMillis,
                        sizeBytes = item.sizeBytes,
                        isKept = false,
                        category = item.category,
                        isOtpBurner = isOtpBurner,
                        isTrashed = false
                    )
                )
            }
        }

        _realScreenshots.value = _realScreenshots.value.map {
            if (it.id == id) it.copy(isOtpBurner = isOtpBurner) else it
        }
    }

    /**
     * Move screenshot to Recycle Bin (Trash).
     * On Android 11+ (API 30+), creates a MediaStore trash request to move the image
     * to the system trash (where it can be restored for 30 days).
     * Also marks it in the internal SnapSweep Recycle Bin for easy recovery.
     */
    suspend fun trashItem(item: ScreenshotItem): PendingIntent? {
        val now = System.currentTimeMillis()
        val uri = item.contentUri

        // Record in internal Room DB as trashed
        val existing = dao.getScreenshotById(item.id)
        if (existing != null) {
            dao.updateTrashedStatus(item.id, isTrashed = true, trashedAt = now)
        } else {
            dao.insertOrUpdate(
                KeptScreenshotEntity(
                    id = item.id,
                    uriString = item.contentUri?.toString() ?: "",
                    displayName = item.displayName,
                    dateAddedMillis = item.dateAddedMillis,
                    sizeBytes = item.sizeBytes,
                    isKept = false,
                    category = item.category,
                    isTrashed = true,
                    trashedAtMillis = now
                )
            )
        }
        dao.recordDeletedItem(item.sizeBytes)

        // On Android 11+ (API 30+), trigger system trash request so file moves to system trash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri != null) {
            try {
                return MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    suspend fun restoreTrashedItem(item: ScreenshotItem): PendingIntent? {
        dao.updateTrashedStatus(item.id, isTrashed = false, trashedAt = null)
        dao.revertDeletedItem(item.sizeBytes)

        val uri = item.contentUri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri != null) {
            try {
                return MediaStore.createTrashRequest(context.contentResolver, listOf(uri), false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun clearAllRecycleBin() {
        dao.clearAllTrashed()
    }

    /**
     * Clean expired items by moving them to the Recycle Bin (Trash).
     * NEVER hard-deletes pre-existing or non-trashed screenshots.
     */
    suspend fun cleanExpiredItems(expiredItems: List<ScreenshotItem>): Pair<Int, Long> {
        val installTs = getInstallTimestamp()
        var trashedCount = 0
        var totalBytes = 0L
        val now = System.currentTimeMillis()

        for (item in expiredItems) {
            // Absolute safety check: Never touch legacy or kept items
            if (item.dateAddedMillis < installTs || item.isLegacy || item.isKept || item.isTrashed) {
                continue
            }

            val existing = dao.getScreenshotById(item.id)
            if (existing != null) {
                dao.updateTrashedStatus(item.id, isTrashed = true, trashedAt = now)
            } else {
                dao.insertOrUpdate(
                    KeptScreenshotEntity(
                        id = item.id,
                        uriString = item.contentUri?.toString() ?: "",
                        displayName = item.displayName,
                        dateAddedMillis = item.dateAddedMillis,
                        sizeBytes = item.sizeBytes,
                        isKept = false,
                        category = item.category,
                        isTrashed = true,
                        trashedAtMillis = now
                    )
                )
            }
            dao.recordDeletedItem(item.sizeBytes)
            trashedCount++
            totalBytes += item.sizeBytes
        }
        return Pair(trashedCount, totalBytes)
    }

    suspend fun onRealItemDeleteConfirmed(item: ScreenshotItem) {
        val now = System.currentTimeMillis()
        dao.updateTrashedStatus(item.id, isTrashed = true, trashedAt = now)
    }

    suspend fun revertSnooze(item: ScreenshotItem, previousSnooze: Long?) {
        dao.updateSnooze(item.id, previousSnooze)
    }
}
