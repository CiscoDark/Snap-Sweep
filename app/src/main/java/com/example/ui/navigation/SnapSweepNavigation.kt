package com.example.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavTabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val NAV_ITEMS = listOf(
    NavTabItem("Queue", Icons.Filled.HourglassBottom, Icons.Outlined.HourglassBottom, "tab_queue"),
    NavTabItem("Swipe Deck", Icons.Filled.Swipe, Icons.Outlined.Swipe, "tab_swipe_deck"),
    NavTabItem("Kept Vault", Icons.Filled.Lock, Icons.Outlined.Lock, "tab_kept_vault"),
    NavTabItem("Smart Rules", Icons.Filled.Rule, Icons.Outlined.Rule, "tab_smart_rules")
)

@Composable
fun SnapSweepBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    queueCount: Int,
    keptCount: Int,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp
    ) {
        NAV_ITEMS.forEachIndexed { index, item ->
            val isSelected = selectedTab == index
            val badgeCount = when (index) {
                0 -> queueCount
                2 -> keptCount
                else -> 0
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(
                                    containerColor = if (index == 2) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = if (index == 2) {
                                        MaterialTheme.colorScheme.onTertiary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                ) {
                                    Text(
                                        text = "$badgeCount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}
