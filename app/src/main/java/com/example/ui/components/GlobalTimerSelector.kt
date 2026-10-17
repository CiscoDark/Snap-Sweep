package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GlobalExpirationOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalTimerSelector(
    selectedOption: GlobalExpirationOption,
    onOptionSelected: (GlobalExpirationOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = GlobalExpirationOption.values()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("global_timer_selector")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = "Expiration Timer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AUTO-EXPIRATION WINDOW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Default: ${selectedOption.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    modifier = Modifier.testTag("timer_option_${option.label}")
                )
            }
        }
    }
}
