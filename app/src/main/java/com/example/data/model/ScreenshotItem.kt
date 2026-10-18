package com.example.data.model

import android.net.Uri

data class ScreenshotItem(
    val id: String,
    val contentUri: Uri?,
    val displayName: String,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val width: Int = 1080,
    val height: Int = 2400,
    val relativePath: String = "Pictures/Screenshots",
    val isKept: Boolean = false,
    val keptAtMillis: Long? = null,
    val snoozeUntilMillis: Long? = null,
    val category: String = "General",
    val isOtpBurner: Boolean = false,
    val isLegacy: Boolean = false, // Taken prior to app installation (Immune to auto-sweep)
    val isTrashed: Boolean = false, // In SnapSweep Recycle Bin (Recoverable)
    val trashedAtMillis: Long? = null
) {
    /**
     * Calculates the expiration timestamp based on global duration or custom snooze.
     */
    fun getExpirationMillis(defaultDurationMillis: Long): Long {
        if (snoozeUntilMillis != null) return snoozeUntilMillis
        val duration = if (isOtpBurner) (15 * 60 * 1000L) else defaultDurationMillis
        return dateAddedMillis + duration
    }

    /**
     * Remaining milliseconds until expiration. Negative if overdue.
     */
    fun getRemainingMillis(nowMillis: Long, defaultDurationMillis: Long): Long {
        return getExpirationMillis(defaultDurationMillis) - nowMillis
    }

    /**
     * Urgent flag: Under 30 minutes left.
     * Legacy and Trashed screenshots NEVER become urgent or expire.
     */
    fun isUrgent(nowMillis: Long, defaultDurationMillis: Long): Boolean {
        if (isLegacy || isKept || isTrashed) return false
        val remaining = getRemainingMillis(nowMillis, defaultDurationMillis)
        return remaining in 0..(30 * 60 * 1000L)
    }

    /**
     * Expired flag.
     * Legacy, Kept, and Trashed screenshots NEVER expire.
     */
    fun isExpired(nowMillis: Long, defaultDurationMillis: Long): Boolean {
        if (isLegacy || isKept || isTrashed) return false
        return getRemainingMillis(nowMillis, defaultDurationMillis) <= 0
    }

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                val kb = sizeBytes / 1024.0
                String.format("%.0f KB", kb)
            }
        }
}
