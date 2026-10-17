package com.example.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == ScreenshotNotificationHelper.ACTION_CLEAN_EXPIRED) {
            com.example.worker.CleanupWorker.triggerImmediate(context)
            showToast(context, "🧹 SnapSweep auto-sweep initiated!")
            return
        }

        val itemId = intent.getStringExtra(ScreenshotNotificationHelper.EXTRA_ITEM_ID) ?: return
        val displayName = intent.getStringExtra(ScreenshotNotificationHelper.EXTRA_DISPLAY_NAME) ?: "Screenshot"

        // Dismiss the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ScreenshotNotificationHelper.NOTIFICATION_ID)

        val repository = ScreenshotRepository(context)

        CoroutineScope(Dispatchers.IO).launch {
            val realList = repository.realScreenshots.value
            val target = realList.find { it.id == itemId }

            when (action) {
                ScreenshotNotificationHelper.ACTION_KEEP -> {
                    if (target != null) {
                        repository.markAsKept(target)
                    }
                    showToast(context, "Locked to Kept Vault: $displayName")
                }

                ScreenshotNotificationHelper.ACTION_BURNER -> {
                    if (target != null) {
                        repository.setOtpBurner(target.id, true)
                    }
                    showToast(context, "🔥 15-Minute Burner timer activated!")
                }

                ScreenshotNotificationHelper.ACTION_DELETE -> {
                    if (target != null) {
                        repository.deleteItem(target)
                    }
                    showToast(context, "Deleted: $displayName")
                }
            }
        }
    }

    private fun showToast(context: Context, text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
