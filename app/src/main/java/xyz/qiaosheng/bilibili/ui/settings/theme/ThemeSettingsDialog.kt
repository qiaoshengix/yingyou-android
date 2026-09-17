package xyz.qiaosheng.bilibili.ui.settings.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode

@Composable
fun ThemeSettingsDialog(
    state: ThemeUiState,
    onModeSelected: (ThemeMode) -> Unit,
    onColorSelected: (ThemeColor) -> Unit,
    onRetryLoading: () -> Unit,
    onRetrySaving: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("选择后自动保存，并应用到整个 App。")
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == state.preferences.mode,
                                    enabled = state.canEdit,
                                    role = Role.RadioButton,
                                    onClick = { onModeSelected(mode) }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = mode == state.preferences.mode,
                                onClick = null,
                                enabled = state.canEdit
                            )
                            Text(when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            })
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "主题色",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                ThemeColorPicker(
                    selectedColor = state.preferences.color,
                    enabled = state.canEdit,
                    onSelected = onColorSelected
                )
                if (state.isLoading || state.isSaving) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(if (state.isLoading) "正在读取…" else "正在保存…")
                    }
                }
                state.loadError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryLoading) { Text("重新读取") }
                }
                state.saveError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetrySaving, enabled = state.canEdit) {
                        Text("重试保存")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
