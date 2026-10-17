package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ScreenshotItem
import java.util.Locale

@Composable
fun ScreenshotCard(
    item: ScreenshotItem,
    nowMillis: Long,
    defaultDurationMillis: Long,
    onKeepToggle: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingMillis = item.getRemainingMillis(nowMillis, defaultDurationMillis)
    val isUrgent = remainingMillis in 0..(30 * 60 * 1000L)
    val isExpired = remainingMillis <= 0

    // Pulsing alpha for urgent countdown badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "urgent_pulse"
    )

    val badgeContainerColor = when {
        item.isKept -> MaterialTheme.colorScheme.tertiaryContainer
        isExpired || isUrgent -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isUrgent) pulseAlpha else 1f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    }

    val badgeContentColor = when {
        item.isKept -> MaterialTheme.colorScheme.onTertiaryContainer
        isExpired || isUrgent -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

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
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("screenshot_card_${item.id}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Thumbnail Area with Live Countdown Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                if (item.contentUri != null) {
                    AsyncImage(
                        model = item.contentUri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Scrim on top for badge readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )

                // Top Countdown Badge (Uses errorContainer for <30m)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeContainerColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("countdown_badge_${item.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isKept) Icons.Default.Lock else Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = badgeContentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = badgeContentColor
                        )
                    }
                }

                // OTP Burner Badge
                if (item.isOtpBurner) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("otp_burner_badge_${item.id}")
                    ) {
                        Text(
                            text = "🔥 OTP Burner",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Category & Size Tag at bottom of thumbnail
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Bottom Actions & Filename Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Lock/Keep, +24h Snooze, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock / Keep Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (item.isKept) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { onKeepToggle() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("keep_btn_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isKept) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (item.isKept) "Locked in Vault" else "Keep item",
                                tint = if (item.isKept) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (item.isKept) "Kept" else "Keep",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (item.isKept) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // +24h Snooze Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onSnooze() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("snooze_btn_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreTime,
                                contentDescription = "Snooze 24 hours",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "+24h",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Instant Delete Trash Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .testTag("delete_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete screenshot",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
