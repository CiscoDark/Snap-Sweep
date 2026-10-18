package com.example.util

import android.content.Context
import android.content.SharedPreferences

object AutoSweepPauseHelper {
    private const val PREFS_NAME = "snapsweep_pause_prefs"
    private const val KEY_PAUSE_UNTIL = "pause_until_millis"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPaused(context: Context): Boolean {
        val pauseUntil = getPrefs(context).getLong(KEY_PAUSE_UNTIL, 0L)
        return System.currentTimeMillis() < pauseUntil
    }

    fun getRemainingMinutes(context: Context): Int {
        val pauseUntil = getPrefs(context).getLong(KEY_PAUSE_UNTIL, 0L)
        val remainingMillis = pauseUntil - System.currentTimeMillis()
        return if (remainingMillis > 0) (remainingMillis / (60 * 1000L)).toInt().coerceAtLeast(1) else 0
    }

    /**
     * Toggles 1-hour pause: If currently paused, unpauses; if not, pauses for 1 hour.
     * Returns true if newly paused, false if unpaused.
     */
    fun togglePause1Hour(context: Context): Boolean {
        return if (isPaused(context)) {
            unpause(context)
            false
        } else {
            pauseForHours(context, 1)
            true
        }
    }

    fun pauseForHours(context: Context, hours: Int = 1) {
        val pauseUntil = System.currentTimeMillis() + (hours * 3600 * 1000L)
        getPrefs(context).edit().putLong(KEY_PAUSE_UNTIL, pauseUntil).apply()
    }

    fun unpause(context: Context) {
        getPrefs(context).edit().remove(KEY_PAUSE_UNTIL).apply()
    }
}
