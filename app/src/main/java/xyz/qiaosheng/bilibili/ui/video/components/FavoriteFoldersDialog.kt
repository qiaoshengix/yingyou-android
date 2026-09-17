package xyz.qiaosheng.bilibili.ui.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.model.library.FavoriteFolder
import xyz.qiaosheng.bilibili.ui.video.VideoActionsState

/** 每次勾选独立保存到本地队列，关闭弹窗不会撤销已经提交的选择。 */
@Composable
internal fun FavoriteFoldersDialog(
    state: VideoActionsState,
    onToggle: (FavoriteFolder) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏到收藏夹") },
        text = {
            Column {
                Text("选择后自动保存，联网时同步。", style = MaterialTheme.typography.bodySmall)
                when {
                    state.foldersLoading -> CircularProgressIndicator(Modifier.padding(20.dp))
                    state.foldersError != null -> ErrorContent(state.foldersError, onRetry)
                    state.folders.isEmpty() -> Text("暂无可用收藏夹，请先在哔哩哔哩创建收藏夹后重试。",
                        Modifier.padding(vertical = 20.dp))
                    else -> LazyColumn {
                        items(state.folders, key = { it.id }) { folder ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onToggle(folder) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = folder.id in state.status.favoriteFolderIds, onCheckedChange = null)
                                Text(folder.title, Modifier.weight(1f))
                                Text(folder.mediaCount.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
