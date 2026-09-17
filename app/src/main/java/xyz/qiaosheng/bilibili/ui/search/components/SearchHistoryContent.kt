package xyz.qiaosheng.bilibili.ui.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity

/** 历史列表、空状态与读取重试共用一处入口；删除按钮与词条搜索是独立事件。 */
@Composable
internal fun SearchHistoryContent(
    history: List<SearchHistoryEntity>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 页面在 modifier 上让出顶栏空间，不能换成 contentPadding，否则滚动内容会穿过顶栏。
    LazyColumn(modifier = modifier) {
        errorMessage?.let { message ->
            item { ErrorContent(message, onRetry, Modifier.fillMaxWidth()) }
        }
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("搜索历史", style = MaterialTheme.typography.titleSmall)
                        Text("仅保留最近 20 条", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onClear) {
                        Text("清空", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(history, key = { it.keyword }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSearch(item.keyword) }
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.keyword, modifier = Modifier.weight(1f), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onDelete(item.keyword) }) {
                        Icon(Icons.Default.Clear, contentDescription = "删除搜索历史：${item.keyword}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else if (errorMessage == null) {
            item { EmptySearchHistory() }
        }
    }
}

@Composable
private fun EmptySearchHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("暂无搜索历史", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("搜索后将在这里保留最近 20 条记录", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ClearSearchHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空搜索历史？") },
        text = { Text("所有搜索历史将被删除，此操作无法撤销。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("清空") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
