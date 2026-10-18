package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.example.data.model.ScreenshotItem
import com.example.ui.UiStats
import com.example.ui.components.EmptyStateView
import com.example.util.BiometricAuthHelper
import com.example.util.PdfExportHelper
import kotlinx.coroutines.launch

val VAULT_CATEGORIES = listOf("All", "Receipts", "Memos", "IDs & Sensitive", "Tech", "Funny")

@Composable
fun KeptVaultScreen(
    keptItems: List<ScreenshotItem>,
    stats: UiStats,
    selectedCategory: String,
    searchQuery: String,
    blurSensitiveEnabled: Boolean,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleBlurSensitive: (Boolean) -> Unit,
    onUnlockToQueue: (ScreenshotItem) -> Unit,
    onDelete: (ScreenshotItem) -> Unit,
    onChangeCategory: (ScreenshotItem, String) -> Unit,
    onItemClick: (ScreenshotItem) -> Unit,
    requireBiometrics: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedItemIds.isNotEmpty()
    var isExportingPdf by remember { mutableStateOf(false) }

    // Session cache of items unlocked via BiometricPrompt
    var unlockedSensitiveIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val totalKeptSizeBytes = keptItems.sumOf { it.sizeBytes }
    val keptSizeMb = totalKeptSizeBytes / (1024.0 * 1024.0)

    fun handleItemClick(item: ScreenshotItem) {
        if (isSelectionMode) {
            selectedItemIds = if (item.id in selectedItemIds) {
                selectedItemIds - item.id
            } else {
                selectedItemIds + item.id
            }
            return
        }

        // Biometric challenge for sensitive items if enabled and not yet authenticated
        if (requireBiometrics && item.category == "IDs & Sensitive" && item.id !in unlockedSensitiveIds) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                BiometricAuthHelper.promptBiometric(
                    activity = activity,
                    title = "Sensitive Vault Item",
                    subtitle = "Verify identity to view ${item.displayName}",
                    onSuccess = {
                        unlockedSensitiveIds = unlockedSensitiveIds + item.id
                        onItemClick(item)
                    },
                    onError = { errMsg ->
                        Toast.makeText(context, "Biometric failed: $errMsg", Toast.LENGTH_SHORT).show()
                    }
                )
                return
            }
        }

        onItemClick(item)
    }

    fun handleItemLongClick(item: ScreenshotItem) {
        selectedItemIds = if (item.id in selectedItemIds) {
            selectedItemIds - item.id
        } else {
            selectedItemIds + item.id
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("kept_vault_screen")
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Multi-Select PDF Export Action Bar
            if (isSelectionMode) {
                item(span = { GridItemSpan(2) }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multi_select_pdf_bar"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { selectedItemIds = emptySet() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Selection",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "${selectedItemIds.size} Selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        selectedItemIds = if (selectedItemIds.size == keptItems.size) {
                                            emptySet()
                                        } else {
                                            keptItems.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (selectedItemIds.size == keptItems.size) "Deselect" else "Select All",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Button(
                                    onClick = {
                                        val itemsToExport = keptItems.filter { it.id in selectedItemIds }
                                        if (itemsToExport.isNotEmpty()) {
                                            isExportingPdf = true
                                            coroutineScope.launch {
                                                val result = PdfExportHelper.exportScreenshotsToPdf(context, itemsToExport)
                                                isExportingPdf = false
                                                if (result.isSuccess) {
                                                    val pdfFile = result.getOrNull()
                                                    if (pdfFile != null) {
                                                        PdfExportHelper.sharePdf(context, pdfFile)
                                                        Toast.makeText(context, "Exported ${itemsToExport.size} screenshots to PDF!", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "PDF export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isExportingPdf,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("export_selected_pdf_btn")
                                ) {
                                    if (isExportingPdf) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isExportingPdf) "Exporting..." else "Export PDF",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Vault Summary Hero Banner
            item(span = { GridItemSpan(2) }) {
                VaultHeroCard(
                    keptCount = keptItems.size,
                    keptSizeMb = keptSizeMb,
                    totalFreedFormatted = stats.formattedFreed,
                    requireBiometrics = requireBiometrics
                )
            }

            // Privacy Blur Toggle Strip
            item(span = { GridItemSpan(2) }) {
                PrivacyBlurToggleCard(
                    blurEnabled = blurSensitiveEnabled,
                    requireBiometrics = requireBiometrics,
                    onToggleBlur = onToggleBlurSensitive
                )
            }

            // Search Bar
            item(span = { GridItemSpan(2) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = "Search by filename or category...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_field")
                )
            }

            // Category Filter Chips Row
            item(span = { GridItemSpan(2) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(VAULT_CATEGORIES) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(category) },
                            label = {
                                Text(
                                    text = category,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("vault_cat_$category")
                        )
                    }
                }
            }

            if (keptItems.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyStateView(
                        title = if (searchQuery.isNotBlank() || selectedCategory != "All") "No Matching Screenshots" else "Kept Vault is Empty",
                        subtitle = if (searchQuery.isNotBlank() || selectedCategory != "All") "Try adjusting your search query or category filter." else "Lock screenshots from your Queue or Swipe Deck to protect them forever.",
                        icon = Icons.Default.Shield,
                        actionButtonText = null,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                // Kept Screenshots Grid
                items(keptItems, key = { it.id }) { item ->
                    val isSelected = item.id in selectedItemIds
                    val isSensitive = item.category == "IDs & Sensitive"
                    val isBlurred = blurSensitiveEnabled && isSensitive && (item.id !in unlockedSensitiveIds)

                    KeptVaultCard(
                        item = item,
                        blurSensitive = isBlurred,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onUnlockToQueue = { onUnlockToQueue(item) },
                        onDelete = { onDelete(item) },
                        onChangeCategory = { newCategory -> onChangeCategory(item, newCategory) },
                        onClick = { handleItemClick(item) },
                        onLongClick = { handleItemLongClick(item) }
                    )
                }
            }

            // Clearance for Navigation Bar
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun VaultHeroCard(
    keptCount: Int,
    keptSizeMb: Double,
    totalFreedFormatted: String,
    requireBiometrics: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_hero_card"),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (requireBiometrics) Icons.Default.Fingerprint else Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (requireBiometrics) "BIOMETRIC VAULT" else "KEPT VAULT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "EXEMPT FROM AUTO-SWEEP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$keptCount Protected",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format("%.1f", keptSizeMb)} MB preserved permanently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Freed so far",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalFreedFormatted,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyBlurToggleCard(
    blurEnabled: Boolean,
    requireBiometrics: Boolean,
    onToggleBlur: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("privacy_blur_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (blurEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Privacy Blur on Sensitive Items",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (requireBiometrics) "Protected by Biometric authentication" else "Blurs IDs, Passwords & Sensitive screenshots",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = blurEnabled,
                onCheckedChange = onToggleBlur,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.testTag("privacy_blur_switch")
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeptVaultCard(
    item: ScreenshotItem,
    blurSensitive: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onUnlockToQueue: () -> Unit,
    onDelete: () -> Unit,
    onChangeCategory: (String) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var showCategoryMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("vault_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            // Thumbnail Area with Privacy Blur
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
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurSensitive) Modifier.blur(25.dp) else Modifier)
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

                // Blur sensitive badge overlay
                if (blurSensitive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Tap to Unlock",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                // Selection Indicator Top Left
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Category Tag at Top Right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Bottom gradient for filename & size readability
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Card Action Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Change category tag
                Box {
                    IconButton(
                        onClick = { showCategoryMenu = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("change_cat_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = "Change Category",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        VAULT_CATEGORIES.filterNot { it == "All" }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    onChangeCategory(cat)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Unlock back to Queue
                    IconButton(
                        onClick = onUnlockToQueue,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("unlock_queue_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Return to Queue",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete / Move to Trash
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("vault_delete_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Move to Trash",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
