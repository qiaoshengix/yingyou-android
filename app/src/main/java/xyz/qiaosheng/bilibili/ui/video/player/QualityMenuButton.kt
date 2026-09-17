package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Composable
internal fun QualityMenuButton(
    qualities: List<VideoQuality>,
    selectedQualityId: Int,
    onQualitySelected: (Int) -> Unit,
    onUserInteraction: () -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = qualities
        .firstOrNull { it.id == selectedQualityId }
        ?.label
        .orEmpty()

    Box {
        TextButton(
            onClick = {
                onUserInteraction()
                expanded = true
                onExpandedChange(true)
            }
        ) {
            Text(
                text = selectedLabel,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; onExpandedChange(false) }
        ) {
            qualities.forEach { quality ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (quality.id == selectedQualityId) {
                                "${quality.label}  ✓"
                            } else {
                                quality.label
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onExpandedChange(false)
                        onUserInteraction()
                        onQualitySelected(quality.id)
                    }
                )
            }
        }
    }
}
