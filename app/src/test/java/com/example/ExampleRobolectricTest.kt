package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.AppStatsEntity
import com.example.data.db.KeptScreenshotEntity
import com.example.data.model.ScreenshotItem
import com.example.worker.CleanupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SnapSweep", appName)
    }

    @Test
    fun `screenshot item expiration and urgency logic`() {
        val now = 1_000_000_000L
        val defaultDuration = 24 * 3600 * 1000L

        // Expired item (added 25h ago)
        val expiredItem = ScreenshotItem(
            id = "test_expired",
            contentUri = null,
            displayName = "expired.png",
            dateAddedMillis = now - (25 * 3600 * 1000L),
            sizeBytes = 2_000_000L
        )
        assertTrue(expiredItem.isExpired(now, defaultDuration))

        // Urgent item (20 mins remaining)
        val urgentItem = ScreenshotItem(
            id = "test_urgent",
            contentUri = null,
            displayName = "urgent.png",
            dateAddedMillis = now - defaultDuration + (20 * 60 * 1000L),
            sizeBytes = 1_500_000L
        )
        assertFalse(urgentItem.isExpired(now, defaultDuration))
        assertTrue(urgentItem.isUrgent(now, defaultDuration))

        // Normal item (5h remaining)
        val normalItem = ScreenshotItem(
            id = "test_normal",
            contentUri = null,
            displayName = "normal.png",
            dateAddedMillis = now - defaultDuration + (5 * 3600 * 1000L),
            sizeBytes = 1_000_000L
        )
        assertFalse(normalItem.isExpired(now, defaultDuration))
        assertFalse(normalItem.isUrgent(now, defaultDuration))
    }

    @Test
    fun `room database keeps and records stats properly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val dao = db.screenshotDao()

        dao.insertOrUpdateStats(
            AppStatsEntity(
                id = 1,
                totalDeletedCount = 2,
                totalBytesFreed = 4_000_000L,
                defaultExpirationMinutes = 1440L,
                themeMode = "SYSTEM",
                pureBlackAmoled = false
            )
        )

        val stats = dao.getAppStats().first()
        assertEquals(2, stats?.totalDeletedCount)
        assertEquals(4_000_000L, stats?.totalBytesFreed)

        // Record a deletion
        dao.recordDeletedItem(1_500_000L)
        val updatedStats = dao.getAppStats().first()
        assertEquals(3, updatedStats?.totalDeletedCount)
        assertEquals(5_500_000L, updatedStats?.totalBytesFreed)

        // Revert a deletion (Undo swipe)
        dao.revertDeletedItem(1_500_000L)
        val revertedStats = dao.getAppStats().first()
        assertEquals(2, revertedStats?.totalDeletedCount)
        assertEquals(4_000_000L, revertedStats?.totalBytesFreed)

        // Lock a screenshot
        val keptEntity = KeptScreenshotEntity(
            id = "screenshot_123",
            uriString = "demo://123",
            displayName = "Receipt.png",
            dateAddedMillis = System.currentTimeMillis(),
            sizeBytes = 2_500_000L,
            isKept = true
        )
        dao.insertOrUpdate(keptEntity)

        val keptList = dao.getKeptScreenshots().first()
        assertEquals(1, keptList.size)
        assertEquals("Receipt.png", keptList[0].displayName)

        // Phase 4 Category Tagging
        dao.updateCategory("screenshot_123", "IDs & Sensitive")
        val updatedKept = dao.getKeptScreenshots().first()
        assertEquals("IDs & Sensitive", updatedKept[0].category)
    }

    @Test
    fun `schedule periodic cleanup worker does not throw`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            val config = androidx.work.Configuration.Builder().build()
            androidx.work.WorkManager.initialize(context, config)
        } catch (_: Exception) {}

        CleanupWorker.schedulePeriodic(context)
        CleanupWorker.triggerImmediate(context)
    }

    @Test
    fun `ocr rule recognition identifies otp and receipts accurately`() {
        val otpText = "Your verification code is 849-201. One-time password expires in 15 mins."
        val otpResult = com.example.ocr.OcrManager.analyzeTextForRules(otpText)
        assertTrue(otpResult.isOtpBurner)
        assertFalse(otpResult.isReceiptAutoKeep)

        val receiptText = "Order #9842. Total Paid: $24.50. Transaction Successful. Thank you for your payment."
        val receiptResult = com.example.ocr.OcrManager.analyzeTextForRules(receiptText)
        assertFalse(receiptResult.isOtpBurner)
        assertTrue(receiptResult.isReceiptAutoKeep)

        // Notification helper channel creation
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.notification.ScreenshotNotificationHelper.createNotificationChannel(context)
    }

    @Test
    fun `power optimization helper and boot receiver test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ignoreIntent = com.example.util.PowerOptimizationHelper.createIgnoreBatteryOptimizationsIntent(context)
        org.junit.Assert.assertNotNull(ignoreIntent)

        val accessIntent = com.example.util.PowerOptimizationHelper.createAccessibilitySettingsIntent()
        org.junit.Assert.assertNotNull(accessIntent)

        // BootReceiver onReceive
        val bootReceiver = com.example.receiver.BootReceiver()
        val bootIntent = android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED)
        bootReceiver.onReceive(context, bootIntent)
    }

    @Test
    fun `material you theme mode and amoled preferences persist in room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = com.example.data.repository.ScreenshotRepository(context)

        repository.initializeStatsIfEmpty()
        repository.updateThemeMode("DARK")
        repository.updatePureBlackAmoled(true)

        val stats = repository.getAppStats().first()
        org.junit.Assert.assertNotNull(stats)
        assertEquals("DARK", stats?.themeMode)
        assertTrue(stats?.pureBlackAmoled == true)
    }
}
