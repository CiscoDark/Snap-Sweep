package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ScreenshotItem
import com.example.ui.components.EmptyStateView
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeDeckScreen(
    items: List<ScreenshotItem>,
    nowMillis: Long,
    defaultDurationMillis: Long,
    canUndo: Boolean,
    onSwipeRightKeep: (ScreenshotItem) -> Unit,
    onSwipeLeftDelete: (ScreenshotItem) -> Unit,
    onSwipeUpSnooze: (ScreenshotItem) -> Unit,
    onUndoLastSwipe: () -> Unit,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentTopItem = items.firstOrNull()

    // Drag animatables for top card
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val horizontalThreshold = 300f
    val verticalThreshold = 250f

    // Action triggering function with smooth fling animations
    fun executeSwipeRight() {
        if (currentTopItem == null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            offsetX.animateTo(1500f, tween(260))
            onSwipeRightKeep(currentTopItem)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    fun executeSwipeLeft() {
        if (currentTopItem == null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            offsetX.animateTo(-1500f, tween(260))
            onSwipeLeftDelete(currentTopItem)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    fun executeSwipeUp() {
        if (currentTopItem == null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            offsetY.animateTo(-1500f, tween(260))
            onSwipeUpSnooze(currentTopItem)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("swipe_deck_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Deck Header: Title & Instructions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Swipe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SWIPE TRIAGE DECK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Swipe Right to Keep • Left to Delete • Up to Snooze",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${items.size} left",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Center: Card Stack Area or Empty Deck Celebration
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty()) {
                EmptyStateView(
                    title = "All Caught Up!",
                    subtitle = "You've reviewed every screenshot in the queue! Important ones are safe in Kept Vault, and clutter was purged.",
                    actionButtonText = "Back to Queue",
                    onActionClick = onNavigateToQueue
                )
            } else {
                // Stack of cards: render back card first for depth
                if (items.size > 1) {
                    val nextItem = items[1]
                    SwipeDeckCard(
                        item = nextItem,
                        nowMillis = nowMillis,
                        defaultDurationMillis = defaultDurationMillis,
                        modifier = Modifier
                            .scale(0.94f)
                            .offset(y = 18.dp)
                            .alpha(0.65f)
                    )
                }

                // Top active draggable card
                currentTopItem?.let { topItem ->
                    val rotationDeg = (offsetX.value / 40f).coerceIn(-15f, 15f)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                            .rotate(rotationDeg)
                            .pointerInput(topItem.id) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        val x = offsetX.value
                                        val y = offsetY.value

                                        if (x > horizontalThreshold) {
                                            executeSwipeRight()
                                        } else if (x < -horizontalThreshold) {
                                            executeSwipeLeft()
                                        } else if (y < -verticalThreshold) {
                                            executeSwipeUp()
                                        } else {
                                            // Reset spring
                                            coroutineScope.launch {
                                                offsetX.animateTo(0f, spring(stiffness = 500f))
                                            }
                                            coroutineScope.launch {
                                                offsetY.animateTo(0f, spring(stiffness = 500f))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f)
                                            offsetY.animateTo(0f)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                            offsetY.snapTo(offsetY.value + dragAmount.y)
                                        }
                                    }
                                )
                            }
                            .testTag("swipe_card_top")
                    ) {
                        SwipeDeckCard(
                            item = topItem,
                            nowMillis = nowMillis,
                            defaultDurationMillis = defaultDurationMillis,
                            modifier = Modifier
                        )

                        // Visual Swipe Overlay Badges
                        val dragX = offsetX.value
                        val dragY = offsetY.value

                        // KEEP Overlay (Swipe Right)
                        if (dragX > 40f && abs(dragX) > abs(dragY)) {
                            val alpha = (dragX / horizontalThreshold).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(24.dp)
                                    .rotate(-15f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha * 0.9f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "KEEP VAULT",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                            }
                        }

                        // DELETE Overlay (Swipe Left)
                        if (dragX < -40f && abs(dragX) > abs(dragY)) {
                            val alpha = (-dragX / horizontalThreshold).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(24.dp)
                                    .rotate(15f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha * 0.9f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "DELETE NOW",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }

                        // SNOOZE Overlay (Swipe Up)
                        if (dragY < -40f && abs(dragY) > abs(dragX)) {
                            val alpha = (-dragY / verticalThreshold).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.9f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "SNOOZE +24H",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Floating Action Buttons Bar: Undo, Delete (Left), Snooze (Up), Keep (Right)
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Undo Button
                FilledTonalIconButton(
                    onClick = onUndoLastSwipe,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("swipe_undo_btn"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Undo Last Swipe",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete Button (Left)
                FilledTonalIconButton(
                    onClick = { executeSwipeLeft() },
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("swipe_delete_action_btn"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Swipe Left - Delete",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Snooze Button (Up)
                FilledTonalIconButton(
                    onClick = { executeSwipeUp() },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("swipe_snooze_action_btn"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreTime,
                        contentDescription = "Swipe Up - Snooze 24h",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Keep Button (Right)
                FilledTonalIconButton(
                    onClick = { executeSwipeRight() },
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("swipe_keep_action_btn"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Swipe Right - Keep in Vault",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeDeckCard(
    item: ScreenshotItem,
    nowMillis: Long,
    defaultDurationMillis: Long,
    modifier: Modifier = Modifier
) {
    val remainingMillis = item.getRemainingMillis(nowMillis, defaultDurationMillis)
    val isExpired = remainingMillis <= 0
    val isUrgent = remainingMillis in 0..(30 * 60 * 1000L)

    val countdownText = when {
        item.isKept -> "Kept Forever"
        isExpired -> "Expired!"
        else -> {
            val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val days = hours / 24

            if (days > 0) {
                String.format(Locale.US, "%dd %02dh left", days, hours % 24)
            } else if (hours > 0) {
                String.format(Locale.US, "%02dh %02dm left", hours, minutes)
            } else {
                String.format(Locale.US, "%02dm %02ds left", minutes, seconds)
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(0.68f)
            .testTag("swipe_card_${item.id}"),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.contentUri != null) {
                AsyncImage(
                    model = item.contentUri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Top scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
            )

            // Top: Countdown Badge & Category Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Countdown Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isExpired || isUrgent) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isKept) Icons.Default.Lock else Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = if (isExpired || isUrgent) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = if (isExpired || isUrgent) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Bottom Information Card Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
