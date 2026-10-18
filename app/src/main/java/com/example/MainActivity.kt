package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ScreenshotViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.ui.components.RecycleBinBottomSheet
import com.example.ui.components.FullScreenScreenshotViewer
import com.example.ui.components.SnapSweepTopAppBar
import com.example.ui.navigation.SnapSweepBottomNavBar
import com.example.ui.screens.KeptVaultScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SmartRulesScreen
import com.example.ui.screens.SwipeDeckScreen
import com.example.ui.theme.SnapSweepTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ScreenshotViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val pureBlackAmoled by viewModel.pureBlackAmoled.collectAsState()

            SnapSweepTheme(
                themeMode = themeMode,
                pureBlackAmoled = pureBlackAmoled,
                dynamicColor = true
            ) {
                SnapSweepApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapSweepApp(
    viewModel: ScreenshotViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val hasReadImages by viewModel.hasReadImagesPermission.collectAsState()
    val hasManageStorage by viewModel.hasManageStoragePermission.collectAsState()
    val selectedGlobalTimer by viewModel.selectedGlobalTimer.collectAsState()
    val nowMillis by viewModel.nowMillis.collectAsState()
    val queueItems by viewModel.queueScreenshots.collectAsState()
    val filteredKeptItems by viewModel.filteredKeptScreenshots.collectAsState()
    val selectedVaultCategory by viewModel.vaultFilterCategory.collectAsState()
    val vaultSearchQuery by viewModel.vaultSearchQuery.collectAsState()
    val blurSensitiveEnabled by viewModel.blurSensitiveEnabled.collectAsState()
    val uiStats by viewModel.uiStats.collectAsState()
    val detailItem by viewModel.detailItem.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingDeleteSender by viewModel.pendingDeleteSender.collectAsState()
    val swipeHistory by viewModel.swipeHistory.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val pureBlackAmoled by viewModel.pureBlackAmoled.collectAsState()
    val requireBiometricsForVault by viewModel.requireBiometricsForVault.collectAsState()
    val trashedScreenshots by viewModel.trashedScreenshots.collectAsState()

    var showRecycleBinSheet by remember { mutableStateOf(false) }
    val recycleBinSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isVaultUnlockedInSession by remember { mutableStateOf(false) }

    val ruleOtpBurnerEnabled by viewModel.ruleOtpBurnerEnabled.collectAsState()
    val ruleAutoKeepReceiptsEnabled by viewModel.ruleAutoKeepReceiptsEnabled.collectAsState()
    val ruleAutoCompressEnabled by viewModel.ruleAutoCompressEnabled.collectAsState()
    val ruleBloatAlertEnabled by viewModel.ruleBloatAlertEnabled.collectAsState()
    val isScanningRules by viewModel.isScanningRules.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val isForegroundServiceRunning by viewModel.isForegroundServiceRunning.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check permissions and scan when returning from settings or resuming app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Scoped storage delete launcher fallback
    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntentSenderResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingDeleteSender) {
        val sender = pendingDeleteSender
        if (sender != null) {
            deleteIntentLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    // Handle snackbar messages
    LaunchedEffect(message) {
        val msg = message
        if (msg != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearMessage()
            }
        }
    }

    // Handle back button on secondary tabs to return to Queue (tab 0)
    BackHandler(enabled = currentTab != 0) {
        viewModel.selectTab(0)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SnapSweepTopAppBar(
                stats = uiStats,
                onRefresh = { viewModel.refreshGallery() },
                trashedCount = trashedScreenshots.size,
                onOpenRecycleBin = { showRecycleBinSheet = true }
            )
        },
        bottomBar = {
            SnapSweepBottomNavBar(
                selectedTab = currentTab,
                onTabSelected = { targetTab ->
                    if (targetTab == 2 && requireBiometricsForVault && !isVaultUnlockedInSession) {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            com.example.util.BiometricAuthHelper.promptBiometric(
                                activity = activity,
                                title = "Kept Vault Lock",
                                subtitle = "Confirm identity with fingerprint or PIN to access Vault",
                                onSuccess = {
                                    isVaultUnlockedInSession = true
                                    viewModel.selectTab(2)
                                },
                                onError = { errMsg ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Vault biometric lock: $errMsg")
                                    }
                                }
                            )
                        } else {
                            viewModel.selectTab(2)
                        }
                    } else {
                        viewModel.selectTab(targetTab)
                    }
                },
                queueCount = uiStats.queueCount,
                keptCount = uiStats.keptCount
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    0 -> QueueScreen(
                        items = queueItems,
                        nowMillis = nowMillis,
                        selectedGlobalTimer = selectedGlobalTimer,
                        onGlobalTimerSelected = { viewModel.setGlobalTimer(it) },
                        hasReadImages = hasReadImages,
                        hasManageStorage = hasManageStorage,
                        onPermissionChanged = { viewModel.refreshPermissions() },
                        onKeepToggle = { viewModel.toggleKeepItem(it) },
                        onSnooze = { viewModel.snoozeItem(it) },
                        onDelete = { viewModel.deleteItem(it) },
                        onItemClick = { viewModel.showDetail(it) },
                        onManualCleanExpired = { viewModel.triggerManualCleanup() },
                        stats = uiStats,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        isForegroundServiceRunning = isForegroundServiceRunning,
                        onRequestDisableBatteryOptimization = { viewModel.requestDisableBatteryOptimization(context) },
                        onRestartForegroundService = { viewModel.restartForegroundService() }
                    )
                    1 -> SwipeDeckScreen(
                        items = queueItems,
                        nowMillis = nowMillis,
                        defaultDurationMillis = selectedGlobalTimer.minutes * 60 * 1000L,
                        canUndo = swipeHistory.isNotEmpty(),
                        onSwipeRightKeep = { viewModel.swipeKeep(it) },
                        onSwipeLeftDelete = { viewModel.swipeDelete(it) },
                        onSwipeUpSnooze = { viewModel.swipeSnooze(it) },
                        onUndoLastSwipe = { viewModel.undoLastSwipe() },
                        onNavigateToQueue = { viewModel.selectTab(0) }
                    )
                    2 -> KeptVaultScreen(
                        keptItems = filteredKeptItems,
                        stats = uiStats,
                        selectedCategory = selectedVaultCategory,
                        searchQuery = vaultSearchQuery,
                        blurSensitiveEnabled = blurSensitiveEnabled,
                        onCategorySelected = { viewModel.setVaultFilterCategory(it) },
                        onSearchQueryChanged = { viewModel.setVaultSearchQuery(it) },
                        onToggleBlurSensitive = { viewModel.toggleBlurSensitive(it) },
                        onUnlockToQueue = { viewModel.toggleKeepItem(it) },
                        onDelete = { viewModel.deleteItem(it) },
                        onChangeCategory = { item, cat -> viewModel.updateItemCategory(item, cat) },
                        onItemClick = { viewModel.showDetail(it) },
                        requireBiometrics = requireBiometricsForVault,
                        onStitchScreenshots = { viewModel.stitchScreenshots(it) }
                    )
                    3 -> SmartRulesScreen(
                        themeMode = themeMode,
                        pureBlackAmoled = pureBlackAmoled,
                        onThemeModeChanged = { viewModel.setThemeMode(it) },
                        onPureBlackAmoledChanged = { viewModel.setPureBlackAmoled(it) },
                        requireBiometricsForVault = requireBiometricsForVault,
                        onToggleRequireBiometrics = { viewModel.toggleRequireBiometrics(it) },
                        ruleOtpBurnerEnabled = ruleOtpBurnerEnabled,
                        ruleAutoKeepReceiptsEnabled = ruleAutoKeepReceiptsEnabled,
                        ruleAutoCompressEnabled = ruleAutoCompressEnabled,
                        ruleBloatAlertEnabled = ruleBloatAlertEnabled,
                        isScanningRules = isScanningRules,
                        onToggleOtpBurner = { viewModel.toggleRuleOtpBurner(it) },
                        onToggleAutoKeepReceipts = { viewModel.toggleRuleAutoKeepReceipts(it) },
                        onToggleAutoCompress = { viewModel.toggleRuleAutoCompress(it) },
                        onToggleBloatAlert = { viewModel.toggleRuleBloatAlert(it) },
                        onRunOcrRulesScan = { viewModel.runSmartOcrRulesScan() },
                        onTriggerTestNotification = { viewModel.triggerTestScreenshotNotification() },
                        stats = uiStats,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        isForegroundServiceRunning = isForegroundServiceRunning,
                        onRequestDisableBatteryOptimization = { viewModel.requestDisableBatteryOptimization(context) },
                        onRestartForegroundService = { viewModel.restartForegroundService() }
                    )
                }
            }

            // Full-Screen zoomable preview modal with metadata, tagging, ML Kit OCR, and share
            detailItem?.let { item ->
                FullScreenScreenshotViewer(
                    item = item,
                    nowMillis = nowMillis,
                    defaultDurationMillis = selectedGlobalTimer.minutes * 60 * 1000L,
                    onDismiss = { viewModel.dismissDetail() },
                    onKeepToggle = { viewModel.toggleKeepItem(item) },
                    onSnooze = { viewModel.snoozeItem(item) },
                    onDelete = { viewModel.deleteItem(item) },
                    onChangeCategory = { newCategory -> viewModel.updateItemCategory(item, newCategory) }
                )
            }

            // In-App "Recently Swept" Recycle Bin Bottom Sheet (Safety Net)
            if (showRecycleBinSheet) {
                RecycleBinBottomSheet(
                    trashedItems = trashedScreenshots,
                    onDismiss = { showRecycleBinSheet = false },
                    onRestoreToKept = { viewModel.restoreTrashedToKept(it) },
                    onRestoreToQueue = { viewModel.restoreTrashedToQueue(it) },
                    onEmptyTrashPermanently = { viewModel.emptyTrashPermanently() },
                    sheetState = recycleBinSheetState
                )
            }
        }
    }
}
