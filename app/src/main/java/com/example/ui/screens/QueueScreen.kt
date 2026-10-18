package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScreenshotItem
import com.example.ui.GlobalExpirationOption
import com.example.ui.UiStats
import com.example.ui.components.BackgroundSurvivalCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlobalTimerSelector
import com.example.ui.components.PermissionBanner
import com.example.ui.components.ScreenshotCard
import com.example.ui.components.StorageReclaimedDashboardCard

@Composable
fun QueueScreen(
    items: List<ScreenshotItem>,
    nowMillis: Long,
    selectedGlobalTimer: GlobalExpirationOption,
    onGlobalTimerSelected: (GlobalExpirationOption) -> Unit,
    hasReadImages: Boolean,
    hasManageStorage: Boolean,
    onPermissionChanged: () -> Unit,
    onKeepToggle: (ScreenshotItem) -> Unit,
    onSnooze: (ScreenshotItem) -> Unit,
    onDelete: (ScreenshotItem) -> Unit,
    onItemClick: (ScreenshotItem) -> Unit,
    onManualCleanExpired: () -> Unit,
    stats: UiStats,
    isIgnoringBatteryOptimizations: Boolean,
    isForegroundServiceRunning: Boolean,
    onRequestDisableBatteryOptimization: () -> Unit,
    onRestartForegroundService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultDurationMillis = selectedGlobalTimer.minutes * 60 * 1000L
    val expiredCount = items.count { it.isExpired(nowMillis, defaultDurationMillis) }
    val urgentCount = items.count { it.isUrgent(nowMillis, defaultDurationMillis) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("queue_screen")
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Storage Reclaimed Summary Card
            item(span = { GridItemSpan(2) }) {
                StorageReclaimedDashboardCard(
                    stats = stats,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Permission Banner (Header)
            item(span = { GridItemSpan(2) }) {
                PermissionBanner(
                    hasReadImages = hasReadImages,
                    hasManageStorage = hasManageStorage,
                    onPermissionChanged = onPermissionChanged
                )
            }

            // Background Survival Checklist (Unrestricted Battery & Foreground Service)
            item(span = { GridItemSpan(2) }) {
                BackgroundSurvivalCard(
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    isForegroundServiceRunning = isForegroundServiceRunning,
                    onRequestDisableBatteryOptimization = onRequestDisableBatteryOptimization,
                    onRestartForegroundService = onRestartForegroundService
                )
            }

            // Global Timer Selector
            item(span = { GridItemSpan(2) }) {
                GlobalTimerSelector(
                    selectedOption = selectedGlobalTimer,
                    onOptionSelected = onGlobalTimerSelected
                )
            }

            // Queue Summary Header Bar
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "EXPIRING QUEUE",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${items.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (urgentCount > 0) {
                            Text(
                                text = "⚠️ $urgentCount expiring in < 30 mins!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Cleaned automatically every 15 mins by Worker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (expiredCount > 0) {
                        Button(
                            onClick = onManualCleanExpired,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("sweep_expired_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "Sweep ($expiredCount)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Empty State
            if (items.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyStateView(
                        title = "Your Queue is Pristine!",
                        subtitle = "No expiring screenshots detected in your gallery. Take a screenshot anytime to auto-track it here.",
                        onActionClick = onPermissionChanged
                    )
                }
            } else {
                // 2-Column Expiring Screenshot Cards
                items(items, key = { it.id }) { item ->
                    ScreenshotCard(
                        item = item,
                        nowMillis = nowMillis,
                        defaultDurationMillis = defaultDurationMillis,
                        onKeepToggle = { onKeepToggle(item) },
                        onSnooze = { onSnooze(item) },
                        onDelete = { onDelete(item) },
                        onClick = { onItemClick(item) }
                    )
                }
            }

            // Extra clearance for FAB and Navigation Bar
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }

        // Floating Action Button: Clean Expired Now
        ExtendedFloatingActionButton(
            onClick = onManualCleanExpired,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .testTag("clean_expired_now_fab"),
            containerColor = if (expiredCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (expiredCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "Clean Expired Screenshots",
                    modifier = Modifier.size(20.dp)
                )
            },
            text = {
                Text(
                    text = if (expiredCount > 0) "Clean Expired ($expiredCount)" else "Clean Expired Now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        )
    }
}
