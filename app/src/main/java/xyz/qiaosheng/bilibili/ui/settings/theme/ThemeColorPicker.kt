package xyz.qiaosheng.bilibili.ui.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.ui.theme.LocalDarkTheme
import xyz.qiaosheng.bilibili.ui.theme.fixedColorScheme

@Composable
internal fun ThemeColorPicker(
    selectedColor: ThemeColor,
    enabled: Boolean,
    onSelected: (ThemeColor) -> Unit
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeColor.entries.chunked(3).forEach { colors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { color ->
                    val selected = color == selectedColor
                    val swatch = if (color == ThemeColor.DYNAMIC) MaterialTheme.colorScheme
                        else fixedColorScheme(color, LocalDarkTheme.current)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 2.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelected(color) }
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).background(swatch.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected || color == ThemeColor.DYNAMIC) {
                                Icon(
                                    imageVector = if (selected) Icons.Default.Check else Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = swatch.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = when (color) {
                                ThemeColor.PINK -> "粉色"
                                ThemeColor.BLUE -> "蓝色"
                                ThemeColor.PURPLE -> "紫色"
                                ThemeColor.GREEN -> "绿色"
                                ThemeColor.ORANGE -> "橙色"
                                ThemeColor.DYNAMIC -> "动态取色"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
