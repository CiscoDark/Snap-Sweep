package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kept_screenshots")
data class KeptScreenshotEntity(
    @PrimaryKey
    val id: String,
    val uriString: String,
    val displayName: String,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val isKept: Boolean = true,
    val keptAtMillis: Long = System.currentTimeMillis(),
    val snoozeUntilMillis: Long? = null,
    val category: String = "General",
    val isOtpBurner: Boolean = false
)
