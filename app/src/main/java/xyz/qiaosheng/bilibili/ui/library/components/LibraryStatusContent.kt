package xyz.qiaosheng.bilibili.ui.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibrarySnapshot

@Composable
internal fun FavoriteFolderPicker(snapshot: LibrarySnapshot, onSelect: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(snapshot.folders, key = { it.id }) { folder ->
            FilterChip(
                selected = snapshot.selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                label = { Text("${folder.title} (${folder.mediaCount})") }
            )
        }
    }
}

@Composable
internal fun LibraryStatusContent(
    kind: LibraryKind,
    snapshot: LibrarySnapshot,
    error: String?,
    syncing: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSync: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        if (snapshot.accountId == null && kind == LibraryKind.HISTORY) {
            LibraryNotice("游客记录保留在本机，登录后查看账号历史", "登录", onLogin)
        }
        if (snapshot.loginRequired && snapshot.items.isNotEmpty()) {
            LibraryNotice("登录后同步账号内容", "登录", onLogin)
        }
        if (snapshot.pendingCount > 0) {
            LibraryNotice(
                message = "有 ${snapshot.pendingCount} 项更改待同步，联网后将继续同步",
                action = "同步",
                onAction = onSync,
                actionEnabled = !syncing
            )
        }
        snapshot.remoteScopeNotice?.takeIf(String::isNotBlank)?.let { notice ->
            LibraryNotice(notice)
        }
        error?.let {
            LibraryNotice(
                message = if (snapshot.items.isNotEmpty()) "暂时无法同步，已保留本地内容。\n$it" else it,
                action = "重试",
                onAction = onRefresh
            )
        }
    }
}

@Composable
private fun LibraryNotice(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (action != null) {
                TextButton(onClick = onAction, enabled = actionEnabled) { Text(action) }
            }
        }
    }
}

@Composable
internal fun LibraryEmptyContent(
    kind: LibraryKind,
    loading: Boolean,
    loginRequired: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("正在加载…")
        } else {
            Text(
                text = when {
                    loginRequired -> when (kind) {
                        LibraryKind.HISTORY -> "登录后同步观看历史"
                        LibraryKind.FAVORITES -> "登录后查看收藏"
                        LibraryKind.LIKES -> "登录后查看点赞"
                    }
                    error != null -> "暂时无法加载"
                    kind == LibraryKind.HISTORY -> "还没有观看记录"
                    kind == LibraryKind.FAVORITES -> "这个收藏夹还是空的"
                    else -> "还没有点赞的视频"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = error ?: when (kind) {
                    LibraryKind.HISTORY -> "观看视频后，会在这里保留进度"
                    LibraryKind.FAVORITES -> "收藏喜欢的视频，方便下次继续观看"
                    LibraryKind.LIKES -> "点赞的视频会保存在这里"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (loginRequired || error != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = if (loginRequired) onLogin else onRetry) {
                    Text(if (loginRequired) "登录" else "重试")
                }
            }
        }
    }
}
