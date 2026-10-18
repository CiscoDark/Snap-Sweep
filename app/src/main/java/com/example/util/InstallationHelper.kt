package com.example.util

import android.content.Context
import android.content.SharedPreferences

object InstallationHelper {
    private const val PREFS_NAME = "snapsweep_install_prefs"
    private const val KEY_INSTALL_TIMESTAMP = "app_install_timestamp"

    /**
     * Retrieves the persistent install timestamp cutoff.
     * Guaranteed to be written only once on the first app launch.
     * All screenshots taken BEFORE this timestamp are treated as legacy pre-existing
     * screenshots and can NEVER be automatically swept.
     */
    fun getOrSetInstallTimestamp(context: Context): Long {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var timestamp = prefs.getLong(KEY_INSTALL_TIMESTAMP, 0L)
        if (timestamp == 0L) {
            timestamp = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_TIMESTAMP, timestamp).apply()
        }
        return timestamp
    }
}
