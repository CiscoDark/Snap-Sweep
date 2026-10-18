package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.repository.ScreenshotRepository
import com.example.notification.NotificationActionReceiver
import com.example.notification.ScreenshotNotificationHelper
import com.example.util.InstallationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SnapSweepForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenshotObserver: ContentObserver? = null
    private var repository: ScreenshotRepository? = null

    override fun onCreate() {
        super.onCreate()
        repository = ScreenshotRepository(applicationContext)
        createNotificationChannel()
        registerScreenshotObserver()
        startStatsTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        try {
            val notification = buildGuardNotification("SnapSweep Active Guard • Monitoring new screenshots")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        try {
            val restartServiceIntent = Intent(applicationContext, SnapSweepForegroundService::class.java).apply {
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                9001,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        unregisterScreenshotObserver()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "SnapSweep Active Guard"
                val desc = "Background monitor for newly taken screenshots and countdown timer protection"
                val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                    description = desc
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildGuardNotification(statsText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ScreenshotNotificationHelper.ACTION_CLEAN_EXPIRED
        }
        val cleanPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            cleanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val queuePendingIntent = PendingIntent.getActivity(
            this,
            103,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("SnapSweep Active Guard")
            .setContentText(statsText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, "Clean Expired", cleanPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open Queue", queuePendingIntent)
            .build()
    }

    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return

        val handler = Handler(Looper.getMainLooper())
        screenshotObserver = object : ContentObserver(handler) {
            private var lastDetectedUri: Uri? = null
            private var lastDetectedTime = 0L

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val targetUri = uri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val now = System.currentTimeMillis()

                if (targetUri == lastDetectedUri && (now - lastDetectedTime) < 2000) {
                    return
                }
                lastDetectedUri = targetUri
                lastDetectedTime = now

                serviceScope.launch {
                    try {
                        val repo = repository ?: ScreenshotRepository(applicationContext)
                        val installTimestamp = repo.getInstallTimestamp()
                        val items = repo.queryRealScreenshots()
                        val newest = items.maxByOrNull { it.dateAddedMillis }

                        // Post-install safety check: ONLY notify for screenshots taken AFTER installation
                        if (newest != null &&
                            newest.dateAddedMillis >= installTimestamp &&
                            !newest.isLegacy &&
                            (now - newest.dateAddedMillis) < 30_000
                        ) {
                            ScreenshotNotificationHelper.showScreenshotNotification(applicationContext, newest)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            screenshotObserver = null
        }
    }

    private fun startStatsTicker() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val repo = repository ?: ScreenshotRepository(applicationContext)
                    val queueSize = repo.realScreenshots.value.count { !it.isKept && !it.isLegacy && !it.isTrashed }
                    val nextSweepMinutes = 15 - ((System.currentTimeMillis() / 60000) % 15)
                    val isPaused = com.example.util.AutoSweepPauseHelper.isPaused(applicationContext)
                    val text = if (isPaused) {
                        val pauseMins = com.example.util.AutoSweepPauseHelper.getRemainingMinutes(applicationContext)
                        "⏸️ Paused via Quick Settings (${pauseMins}m left) • Auto-sweep halted"
                    } else {
                        "Watching $queueSize new screenshots • Next auto-sweep in ${nextSweepMinutes}m"
                    }
                    val notification = buildGuardNotification(text)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, notification)
                } catch (_: Exception) {}

                delay(60_000)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "snapsweep_guard_channel"
        const val NOTIFICATION_ID = 5001

        fun start(context: Context) {
            val intent = Intent(context, SnapSweepForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SnapSweepForegroundService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
