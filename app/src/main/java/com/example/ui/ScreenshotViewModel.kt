package com.example.ui

import android.app.Application
import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppStatsEntity
import com.example.data.db.KeptScreenshotEntity
import com.example.data.model.ScreenshotItem
import com.example.data.repository.ScreenshotRepository
import com.example.data.scanner.MediaStoreScanner
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GlobalExpirationOption(val label: String, val minutes: Long) {
    MINUTES_15("15m", 15L),
    HOURS_1("1h", 60L),
    HOURS_24("24h", 1440L),
    DAYS_7("7d", 10080L)
}

data class UiStats(
    val totalDeletedCount: Int = 0,
    val totalBytesFreed: Long = 0L,
    val queueCount: Int = 0,
    val keptCount: Int = 0
) {
    val formattedFreed: String
        get() {
            val mb = totalBytesFreed / (1024.0 * 1024.0)
            return if (mb >= 1000.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}

enum class SwipeActionType {
    KEEP,
    DELETE,
    SNOOZE
}

data class SwipeHistoryItem(
    val item: ScreenshotItem,
    val type: SwipeActionType,
    val previousSnoozeMillis: Long?
)

class ScreenshotViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(application)

    // Current navigation tab: 0=Queue, 1=Swipe Deck, 2=Kept Vault, 3=Smart Rules
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Permissions state
    private val _hasReadImagesPermission = MutableStateFlow(
        MediaStoreScanner.hasReadImagesPermission(application)
    )
    val hasReadImagesPermission: StateFlow<Boolean> = _hasReadImagesPermission.asStateFlow()

    private val _hasManageStoragePermission = MutableStateFlow(
        MediaStoreScanner.hasManageStoragePermission()
    )
    val hasManageStoragePermission: StateFlow<Boolean> = _hasManageStoragePermission.asStateFlow()

    // Selected global timer
    private val _selectedGlobalTimer = MutableStateFlow(GlobalExpirationOption.HOURS_24)
    val selectedGlobalTimer: StateFlow<GlobalExpirationOption> = _selectedGlobalTimer.asStateFlow()

    // Live ticking timestamp for animated countdowns
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    // App stats from Room DB
    val dbStats: StateFlow<AppStatsEntity?> = repository.getAppStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Room DB screenshot records
    val dbKeptEntities: StateFlow<List<KeptScreenshotEntity>> = repository.getKeptScreenshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dbAllEntities: StateFlow<List<KeptScreenshotEntity>> = repository.getAllScreenshotDbRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 7: Material You Theme Mode and AMOLED Pitch Black state
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _pureBlackAmoled = MutableStateFlow(false)
    val pureBlackAmoled: StateFlow<Boolean> = _pureBlackAmoled.asStateFlow()

    // Fallback scoped storage delete request
    private val _pendingDeleteSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteSender: StateFlow<IntentSender?> = _pendingDeleteSender.asStateFlow()
    private var itemAwaitingDelete: ScreenshotItem? = null

    // Detail dialog / Full screen modal
    private val _detailItem = MutableStateFlow<ScreenshotItem?>(null)
    val detailItem: StateFlow<ScreenshotItem?> = _detailItem.asStateFlow()

    // Notification toast / snackbar
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Phase 6: Unkillable Background Survival
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(
        com.example.util.PowerOptimizationHelper.isIgnoringBatteryOptimizations(application)
    )
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(
        com.example.util.PowerOptimizationHelper.isAccessibilityServiceEnabled(application)
    )
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isForegroundServiceRunning = MutableStateFlow(true)
    val isForegroundServiceRunning: StateFlow<Boolean> = _isForegroundServiceRunning.asStateFlow()

    // Phase 4: Privacy Blur & Category Filtering
    private val _blurSensitiveEnabled = MutableStateFlow(true)
    val blurSensitiveEnabled: StateFlow<Boolean> = _blurSensitiveEnabled.asStateFlow()

    private val _vaultFilterCategory = MutableStateFlow("All")
    val vaultFilterCategory: StateFlow<String> = _vaultFilterCategory.asStateFlow()

    private val _vaultSearchQuery = MutableStateFlow("")
    val vaultSearchQuery: StateFlow<String> = _vaultSearchQuery.asStateFlow()

    // Phase 5: Smart Rules & ML Kit OCR
    private val _ruleOtpBurnerEnabled = MutableStateFlow(true)
    val ruleOtpBurnerEnabled: StateFlow<Boolean> = _ruleOtpBurnerEnabled.asStateFlow()

    private val _ruleAutoKeepReceiptsEnabled = MutableStateFlow(true)
    val ruleAutoKeepReceiptsEnabled: StateFlow<Boolean> = _ruleAutoKeepReceiptsEnabled.asStateFlow()

    private val _ruleAutoCompressEnabled = MutableStateFlow(false)
    val ruleAutoCompressEnabled: StateFlow<Boolean> = _ruleAutoCompressEnabled.asStateFlow()

    private val _ruleBloatAlertEnabled = MutableStateFlow(true)
    val ruleBloatAlertEnabled: StateFlow<Boolean> = _ruleBloatAlertEnabled.asStateFlow()

    private val _isScanningRules = MutableStateFlow(false)
    val isScanningRules: StateFlow<Boolean> = _isScanningRules.asStateFlow()

    private val _swipeHistory = MutableStateFlow<List<SwipeHistoryItem>>(emptyList())
    val swipeHistory: StateFlow<List<SwipeHistoryItem>> = _swipeHistory.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeStatsIfEmpty()
        }

        // Schedule background auto-clean periodic worker (every 15 mins)
        com.example.worker.CleanupWorker.schedulePeriodic(application)

        // Timer ticker coroutine: emits every second
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _nowMillis.value = System.currentTimeMillis()
            }
        }

        // Sync theme and expiration timer from DB when loaded
        viewModelScope.launch {
            dbStats.collect { stats ->
                if (stats != null) {
                    val matchingOption = GlobalExpirationOption.values().find {
                        it.minutes == stats.defaultExpirationMinutes
                    } ?: GlobalExpirationOption.HOURS_24
                    _selectedGlobalTimer.value = matchingOption

                    try {
                        _themeMode.value = AppThemeMode.valueOf(stats.themeMode)
                    } catch (e: Exception) {
                        _themeMode.value = AppThemeMode.SYSTEM
                    }
                    _pureBlackAmoled.value = stats.pureBlackAmoled
                }
            }
        }

        // Start persistent Foreground Service
        com.example.service.SnapSweepForegroundService.start(application)

        refreshPermissions()
    }

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _hasReadImagesPermission.value = MediaStoreScanner.hasReadImagesPermission(app)
        _hasManageStoragePermission.value = MediaStoreScanner.hasManageStoragePermission()
        _isIgnoringBatteryOptimizations.value = com.example.util.PowerOptimizationHelper.isIgnoringBatteryOptimizations(app)
        _isAccessibilityEnabled.value = com.example.util.PowerOptimizationHelper.isAccessibilityServiceEnabled(app)
        if (_hasReadImagesPermission.value) {
            viewModelScope.launch {
                repository.scanRealMedia()
            }
        }
    }

    fun refreshGallery() {
        refreshPermissions()
        _message.value = "Gallery scan refreshed"
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            repository.updateThemeMode(mode.name)
        }
    }

    fun setPureBlackAmoled(enabled: Boolean) {
        _pureBlackAmoled.value = enabled
        viewModelScope.launch {
            repository.updatePureBlackAmoled(enabled)
        }
    }

    fun requestDisableBatteryOptimization(context: Context) {
        val intent = com.example.util.PowerOptimizationHelper.createIgnoreBatteryOptimizationsIntent(context)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            _message.value = "Unable to open battery optimization settings directly"
        }
    }

    fun requestOpenAccessibilitySettings(context: Context) {
        val intent = com.example.util.PowerOptimizationHelper.createAccessibilitySettingsIntent()
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            _message.value = "Unable to open accessibility settings directly"
        }
    }

    fun restartForegroundService() {
        val app = getApplication<Application>()
        com.example.service.SnapSweepForegroundService.start(app)
        _isForegroundServiceRunning.value = true
        _message.value = "Active Guard Foreground Service restarted"
    }

    fun setGlobalTimer(option: GlobalExpirationOption) {
        _selectedGlobalTimer.value = option
        viewModelScope.launch {
            repository.setDefaultExpiration(option.minutes)
        }
        _message.value = "Default timer set to ${option.label}"
    }

    fun toggleKeepItem(item: ScreenshotItem) {
        viewModelScope.launch {
            if (item.isKept) {
                repository.unkeepToQueue(item.id)
                _message.value = "Moved back to Expiring Queue"
            } else {
                repository.markAsKept(item)
                _message.value = "Locked in Kept Vault"
            }
        }
    }

    fun snoozeItem(item: ScreenshotItem, hours: Long = 24L) {
        viewModelScope.launch {
            repository.snoozeItem(item, hours)
            _message.value = "Snoozed +${hours}h"
        }
    }

    fun deleteItem(item: ScreenshotItem) {
        viewModelScope.launch {
            val pendingIntent = repository.deleteItem(item)
            if (pendingIntent != null) {
                itemAwaitingDelete = item
                _pendingDeleteSender.value = pendingIntent.intentSender
            } else {
                _message.value = "Deleted screenshot (${item.formattedSize} freed)"
            }
        }
    }

    fun onIntentSenderResult(success: Boolean) {
        val item = itemAwaitingDelete
        _pendingDeleteSender.value = null
        itemAwaitingDelete = null
        if (success && item != null) {
            viewModelScope.launch {
                repository.onRealItemDeleteConfirmed(item)
                _message.value = "Deleted screenshot (${item.formattedSize} freed)"
            }
        }
    }

    fun showDetail(item: ScreenshotItem) {
        _detailItem.value = item
    }

    fun dismissDetail() {
        _detailItem.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun triggerManualCleanup() {
        val app = getApplication<Application>()
        com.example.worker.CleanupWorker.triggerImmediate(app)
        viewModelScope.launch {
            val now = _nowMillis.value
            val defaultDuration = _selectedGlobalTimer.value.minutes * 60 * 1000L
            val currentQueue = queueScreenshots.value
            val expired = currentQueue.filter { it.isExpired(now, defaultDuration) }
            if (expired.isNotEmpty()) {
                val (count, bytes) = repository.cleanExpiredItems(expired)
                val mb = bytes / (1024.0 * 1024.0)
                _message.value = "Purged $count expired screenshots (Freed ${String.format("%.1f MB", mb)})"
            } else {
                _message.value = "Auto-Cleaner ran: No expired items to purge"
            }
        }
    }

    fun swipeKeep(item: ScreenshotItem) {
        val entry = SwipeHistoryItem(item, SwipeActionType.KEEP, item.snoozeUntilMillis)
        _swipeHistory.value = _swipeHistory.value + entry
        toggleKeepItem(item)
    }

    fun swipeDelete(item: ScreenshotItem) {
        val entry = SwipeHistoryItem(item, SwipeActionType.DELETE, item.snoozeUntilMillis)
        _swipeHistory.value = _swipeHistory.value + entry
        deleteItem(item)
    }

    fun swipeSnooze(item: ScreenshotItem) {
        val entry = SwipeHistoryItem(item, SwipeActionType.SNOOZE, item.snoozeUntilMillis)
        _swipeHistory.value = _swipeHistory.value + entry
        snoozeItem(item, 24L)
    }

    fun undoLastSwipe() {
        val currentHistory = _swipeHistory.value
        if (currentHistory.isEmpty()) return
        val lastEntry = currentHistory.last()
        _swipeHistory.value = currentHistory.dropLast(1)

        viewModelScope.launch {
            when (lastEntry.type) {
                SwipeActionType.KEEP -> {
                    repository.unkeepToQueue(lastEntry.item.id)
                    _message.value = "Undid Keep: ${lastEntry.item.displayName}"
                }
                SwipeActionType.DELETE -> {
                    repository.restoreDeletedItem(lastEntry.item)
                    _message.value = "Restored: ${lastEntry.item.displayName}"
                }
                SwipeActionType.SNOOZE -> {
                    repository.revertSnooze(lastEntry.item, lastEntry.previousSnoozeMillis)
                    _message.value = "Reverted snooze: ${lastEntry.item.displayName}"
                }
            }
        }
    }

    // Combined queue items: 100% Real MediaStore, merged with Room snooze and keep states
    val queueScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        repository.realScreenshots,
        dbAllEntities
    ) { realList, dbEntities ->
        val dbMap = dbEntities.associateBy { it.id }

        realList.map { item ->
            val dbRecord = dbMap[item.id]
            if (dbRecord != null) {
                item.copy(
                    isKept = dbRecord.isKept,
                    keptAtMillis = dbRecord.keptAtMillis,
                    snoozeUntilMillis = dbRecord.snoozeUntilMillis,
                    isOtpBurner = dbRecord.isOtpBurner || item.isOtpBurner,
                    category = if (dbRecord.category.isNotBlank() && dbRecord.category != "General") dbRecord.category else item.category
                )
            } else {
                item
            }
        }.filterNot { it.isKept }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Kept Vault items from Room DB
    val keptScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        repository.realScreenshots,
        dbKeptEntities
    ) { realList, dbKept ->
        val realMap = realList.associateBy { it.id }

        dbKept.map { entity ->
            val existing = realMap[entity.id]
            existing?.copy(isKept = true, keptAtMillis = entity.keptAtMillis, category = entity.category)
                ?: ScreenshotItem(
                    id = entity.id,
                    contentUri = if (entity.uriString.isNotBlank()) android.net.Uri.parse(entity.uriString) else null,
                    displayName = entity.displayName,
                    dateAddedMillis = entity.dateAddedMillis,
                    sizeBytes = entity.sizeBytes,
                    isKept = true,
                    keptAtMillis = entity.keptAtMillis,
                    category = entity.category
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBlurSensitive(enabled: Boolean) {
        _blurSensitiveEnabled.value = enabled
        _message.value = if (enabled) "Sensitive items blurred" else "Sensitive blur disabled"
    }

    fun setVaultFilterCategory(category: String) {
        _vaultFilterCategory.value = category
    }

    fun setVaultSearchQuery(query: String) {
        _vaultSearchQuery.value = query
    }

    fun updateItemCategory(item: ScreenshotItem, newCategory: String) {
        viewModelScope.launch {
            repository.updateCategory(item.id, newCategory)
            _message.value = "Tagged as \"$newCategory\""
            if (_detailItem.value?.id == item.id) {
                _detailItem.value = _detailItem.value?.copy(category = newCategory)
            }
        }
    }

    val filteredKeptScreenshots: StateFlow<List<ScreenshotItem>> = combine(
        keptScreenshots,
        _vaultFilterCategory,
        _vaultSearchQuery
    ) { items, filter, query ->
        items.filter { item ->
            val matchesCategory = filter == "All" || item.category.equals(filter, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.displayName.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleRuleOtpBurner(enabled: Boolean) {
        _ruleOtpBurnerEnabled.value = enabled
        _message.value = if (enabled) "15m OTP Burner rule enabled" else "15m OTP Burner rule disabled"
    }

    fun toggleRuleAutoKeepReceipts(enabled: Boolean) {
        _ruleAutoKeepReceiptsEnabled.value = enabled
        _message.value = if (enabled) "Auto-Keep Receipts rule enabled" else "Auto-Keep Receipts rule disabled"
    }

    fun toggleRuleAutoCompress(enabled: Boolean) {
        _ruleAutoCompressEnabled.value = enabled
    }

    fun toggleRuleBloatAlert(enabled: Boolean) {
        _ruleBloatAlertEnabled.value = enabled
    }

    fun runSmartOcrRulesScan() {
        viewModelScope.launch {
            _isScanningRules.value = true
            val app = getApplication<Application>()
            val currentQueue = queueScreenshots.value
            var otpCount = 0
            var receiptCount = 0

            for (item in currentQueue) {
                val text = com.example.ocr.OcrManager.extractText(app, item)
                val result = com.example.ocr.OcrManager.analyzeTextForRules(
                    text = text,
                    isOtpRuleEnabled = _ruleOtpBurnerEnabled.value,
                    isReceiptRuleEnabled = _ruleAutoKeepReceiptsEnabled.value
                )

                if (result.isOtpBurner && !item.isOtpBurner) {
                    repository.setOtpBurner(item.id, true)
                    otpCount++
                }

                if (result.isReceiptAutoKeep && !item.isKept) {
                    repository.updateCategory(item.id, "Receipts")
                    repository.markAsKept(item.copy(category = "Receipts"))
                    receiptCount++
                }
            }

            _isScanningRules.value = false
            val summary = buildString {
                append("ML Kit OCR Scan Complete: ")
                if (otpCount > 0) append("$otpCount OTP Burner(s) tagged (15m timer). ")
                if (receiptCount > 0) append("$receiptCount Receipt(s) saved to Kept Vault. ")
                if (otpCount == 0 && receiptCount == 0) append("All screenshots analyzed; rules applied.")
            }
            _message.value = summary
        }
    }

    fun triggerTestScreenshotNotification() {
        val app = getApplication<Application>()
        val currentQueue = queueScreenshots.value
        val target = currentQueue.firstOrNull() ?: ScreenshotItem(
            id = "test_screenshot_item",
            contentUri = null,
            displayName = "Receipt_UberEats_Oct17.png",
            dateAddedMillis = System.currentTimeMillis(),
            sizeBytes = 2_850_000L,
            category = "Receipts"
        )
        com.example.notification.ScreenshotNotificationHelper.showScreenshotNotification(app, target)
        _message.value = "Notification posted! Check shade for [Keep Forever], [🔥 15m Burner], [Delete Now]"
    }

    // UI Stats combined
    val uiStats: StateFlow<UiStats> = combine(
        dbStats,
        queueScreenshots,
        keptScreenshots
    ) { stats, queue, kept ->
        UiStats(
            totalDeletedCount = stats?.totalDeletedCount ?: 0,
            totalBytesFreed = stats?.totalBytesFreed ?: 0L,
            queueCount = queue.size,
            keptCount = kept.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiStats())
}
