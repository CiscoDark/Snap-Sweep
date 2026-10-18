package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_stats")
data class AppStatsEntity(
    @PrimaryKey
    val id: Int = 1,
    val totalDeletedCount: Int = 0,
    val totalBytesFreed: Long = 0L,
    val defaultExpirationMinutes: Long = 1440L, // 24 hours default
    val themeMode: String = "SYSTEM",
    val pureBlackAmoled: Boolean = false,
    val autoSweepEnabled: Boolean = false, // DISABLED BY DEFAULT on install
    val installTimestamp: Long = 0L
)
