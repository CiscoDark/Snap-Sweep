package com.example.service

import android.accessibilityservice.AccessibilityService
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.accessibility.AccessibilityEvent
import com.example.data.repository.ScreenshotRepository
import com.example.notification.ScreenshotNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SnapSweepAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenshotObserver: ContentObserver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerInstantScreenshotObserver()

        // Also ensure foreground service is active
        SnapSweepForegroundService.start(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observer mode: provides system-level process priority and keep-alive
    }

    override fun onInterrupt() {
        // Called when the system wants to interrupt the feedback
    }

    override fun onDestroy() {
        unregisterScreenshotObserver()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerInstantScreenshotObserver() {
        if (screenshotObserver != null) return

        val handler = Handler(Looper.getMainLooper())
        screenshotObserver = object : ContentObserver(handler) {
            private var lastUri: Uri? = null
            private var lastTime = 0L

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val targetUri = uri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val now = System.currentTimeMillis()

                if (targetUri == lastUri && (now - lastTime) < 1500) {
                    return
                }
                lastUri = targetUri
                lastTime = now

                serviceScope.launch {
                    try {
                        val repository = ScreenshotRepository(applicationContext)
                        val items = repository.queryRealScreenshots()
                        val newest = items.maxByOrNull { it.dateAddedMillis }
                        if (newest != null && (now - newest.dateAddedMillis) < 20_000) {
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
}
