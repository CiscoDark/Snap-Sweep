package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.util.AutoSweepPauseHelper

class SnapSweepTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isNowPaused = AutoSweepPauseHelper.togglePause1Hour(applicationContext)
        updateTileState()
        try {
            SnapSweepForegroundService.start(applicationContext)
        } catch (_: Exception) {}

        val toastMsg = if (isNowPaused) {
            "⏸️ SnapSweep paused for 1 hour. Safe to capture temporary screenshots!"
        } else {
            "🛡️ SnapSweep active guarding resumed."
        }
        Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isPaused = AutoSweepPauseHelper.isPaused(applicationContext)

        if (isPaused) {
            val mins = AutoSweepPauseHelper.getRemainingMinutes(applicationContext)
            tile.state = Tile.STATE_INACTIVE
            tile.label = "SnapSweep"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Paused (${mins}m)"
            }
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "SnapSweep"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Guarding"
            }
        }
        tile.updateTile()
    }
}
