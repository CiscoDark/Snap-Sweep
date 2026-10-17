package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM kept_screenshots WHERE isKept = 1 ORDER BY keptAtMillis DESC")
    fun getKeptScreenshots(): Flow<List<KeptScreenshotEntity>>

    @Query("SELECT * FROM kept_screenshots")
    fun getAllScreenshotStates(): Flow<List<KeptScreenshotEntity>>

    @Query("SELECT * FROM kept_screenshots WHERE id = :id LIMIT 1")
    suspend fun getScreenshotById(id: String): KeptScreenshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: KeptScreenshotEntity)

    @Query("DELETE FROM kept_screenshots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE kept_screenshots SET isKept = :isKept, keptAtMillis = :keptAt WHERE id = :id")
    suspend fun updateKeptStatus(id: String, isKept: Boolean, keptAt: Long)

    @Query("UPDATE kept_screenshots SET snoozeUntilMillis = :snoozeUntil WHERE id = :id")
    suspend fun updateSnooze(id: String, snoozeUntil: Long?)

    @Query("UPDATE kept_screenshots SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: String, category: String)

    @Query("UPDATE kept_screenshots SET isOtpBurner = :isOtpBurner WHERE id = :id")
    suspend fun updateOtpBurner(id: String, isOtpBurner: Boolean)

    // Stats
    @Query("SELECT * FROM app_stats WHERE id = 1 LIMIT 1")
    fun getAppStats(): Flow<AppStatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStats(stats: AppStatsEntity)

    @Query("UPDATE app_stats SET totalDeletedCount = totalDeletedCount + 1, totalBytesFreed = totalBytesFreed + :bytesFreed WHERE id = 1")
    suspend fun recordDeletedItem(bytesFreed: Long)

    @Query("UPDATE app_stats SET totalDeletedCount = CASE WHEN totalDeletedCount > 0 THEN totalDeletedCount - 1 ELSE 0 END, totalBytesFreed = CASE WHEN totalBytesFreed > :bytesFreed THEN totalBytesFreed - :bytesFreed ELSE 0 END WHERE id = 1")
    suspend fun revertDeletedItem(bytesFreed: Long)

    @Query("UPDATE app_stats SET defaultExpirationMinutes = :minutes WHERE id = 1")
    suspend fun updateDefaultExpiration(minutes: Long)

    @Query("UPDATE app_stats SET themeMode = :mode WHERE id = 1")
    suspend fun updateThemeMode(mode: String)

    @Query("UPDATE app_stats SET pureBlackAmoled = :enabled WHERE id = 1")
    suspend fun updatePureBlackAmoled(enabled: Boolean)
}
