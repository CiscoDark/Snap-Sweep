package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScreenshotItem

object ScreenshotNotificationHelper {

    const val CHANNEL_ID = "snapsweep_triage_channel"
    const val NOTIFICATION_ID = 4096

    const val ACTION_KEEP = "com.example.notification.ACTION_KEEP"
    const val ACTION_BURNER = "com.example.notification.ACTION_BURNER"
    const val ACTION_DELETE = "com.example.notification.ACTION_DELETE"
    const val ACTION_CLEAN_EXPIRED = "com.example.notification.ACTION_CLEAN_EXPIRED"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_DISPLAY_NAME = "extra_display_name"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Screenshot Quick Actions"
            val descriptionText = "Interactive notification shade buttons to Keep, Burn, or Delete"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showScreenshotNotification(context: Context, item: ScreenshotItem) {
        createNotificationChannel(context)

        // Tap content intent -> Open MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: [Keep Forever]
        val keepIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_KEEP
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_DISPLAY_NAME, item.displayName)
        }
        val keepPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            keepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: [15m Burner]
        val burnerIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_BURNER
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_DISPLAY_NAME, item.displayName)
        }
        val burnerPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            burnerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: [Delete Now]
        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DELETE
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_DISPLAY_NAME, item.displayName)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("New Screenshot Detected")
            .setContentText("${item.displayName} • Choose action:")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Captured ${item.displayName} (${item.formattedSize}). Fast triage right from this notification:")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Keep Forever", keepPendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "🔥 15m Burner", burnerPendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Delete Now", deletePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
