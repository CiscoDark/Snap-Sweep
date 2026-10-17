package com.example.data.repository

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.AppDatabase
import com.example.data.db.AppStatsEntity
import com.example.data.db.KeptScreenshotEntity
import com.example.data.model.ScreenshotItem
import com.example.data.scanner.MediaStoreScanner
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

    fun getAllScreenshotDbRecords(): Flow<List<KeptScreenshotEntity>> = dao.getAllScreenshotStates()

    suspend fun initializeStatsIfEmpty() {
        val stats = dao.getAppStats().firstOrNull()
        if (stats == null) {
            dao.insertOrUpdateStats(
                AppStatsEntity(
                    id = 1,
                    totalDeletedCount = 0,
                    totalBytesFreed = 0L,
                    defaultExpirationMinutes = 1440L, // 24 hours
                    themeMode = "SYSTEM",
                    pureBlackAmoled = false
                )
            )
        }
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
            val scanned = MediaStoreScanner.scanScreenshots(context)
            _realScreenshots.value = scanned
            return scanned
        }
        return emptyList()
    }

    suspend fun scanRealMedia() {
        if (MediaStoreScanner.hasReadImagesPermission(context)) {
            val scanned = MediaStoreScanner.scanScreenshots(context)
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
            category = finalCategory
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

        // Save in DB
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
                    category = item.category
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
                        isOtpBurner = isOtpBurner
                    )
                )
            }
        }

        _realScreenshots.value = _realScreenshots.value.map {
            if (it.id == id) it.copy(isOtpBurner = isOtpBurner) else it
        }
    }

    /**
     * Delete item. Returns PendingIntent if MediaStore scoped storage requires user confirmation prompt.
     */
    suspend fun deleteItem(item: ScreenshotItem): PendingIntent? {
        val uri = item.contentUri ?: return null

        if (MediaStoreScanner.hasManageStoragePermission()) {
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    _realScreenshots.value = _realScreenshots.value.filterNot { it.id == item.id }
                    dao.deleteById(item.id)
                    dao.recordDeletedItem(item.sizeBytes)
                    return null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Scoped storage fallback via createDeleteRequest on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                return pendingIntent
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    suspend fun cleanExpiredItems(expiredItems: List<ScreenshotItem>): Pair<Int, Long> {
        var deletedCount = 0
        var totalBytes = 0L

        for (item in expiredItems) {
            val uri = item.contentUri ?: continue
            if (MediaStoreScanner.hasManageStoragePermission()) {
                try {
                    val deleted = context.contentResolver.delete(uri, null, null)
                    if (deleted > 0) {
                        _realScreenshots.value = _realScreenshots.value.filterNot { it.id == item.id }
                        dao.deleteById(item.id)
                        dao.recordDeletedItem(item.sizeBytes)
                        deletedCount++
                        totalBytes += item.sizeBytes
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return Pair(deletedCount, totalBytes)
    }

    suspend fun onRealItemDeleteConfirmed(item: ScreenshotItem) {
        _realScreenshots.value = _realScreenshots.value.filterNot { it.id == item.id }
        dao.deleteById(item.id)
        dao.recordDeletedItem(item.sizeBytes)
    }

    suspend fun restoreDeletedItem(item: ScreenshotItem) {
        _realScreenshots.value = listOf(item) + _realScreenshots.value.filterNot { it.id == item.id }
        dao.revertDeletedItem(item.sizeBytes)
    }

    suspend fun revertSnooze(item: ScreenshotItem, previousSnooze: Long?) {
        dao.updateSnooze(item.id, previousSnooze)
    }
}
