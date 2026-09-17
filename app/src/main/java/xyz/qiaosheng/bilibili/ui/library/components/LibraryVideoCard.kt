package xyz.qiaosheng.bilibili.ui.library.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.core.format.formatDuration
import xyz.qiaosheng.bilibili.core.format.formatRelativeTime
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import coil3.compose.AsyncImage

@Composable
internal fun LibraryVideoCard(
    video: LibraryVideo,
    kind: LibraryKind,
    operationInProgress: Boolean,
    onOpen: (LibraryVideo) -> Unit,
    onRemove: (LibraryVideo) -> Unit
) {
    var menuExpanded by remember(video.bvid) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable { onOpen(video) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                )
                if (video.durationSeconds > 0) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title.ifBlank { video.bvid },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = video.ownerName.ifBlank { "UP 主信息暂不可用" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (kind == LibraryKind.HISTORY && video.viewedAt > 0) {
                    Text(
                        text = formatRelativeTime(video.viewedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !operationInProgress) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作：${video.title}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(when (kind) {
                                LibraryKind.HISTORY -> "删除观看记录"
                                LibraryKind.FAVORITES -> "取消收藏"
                                LibraryKind.LIKES -> "取消点赞"
                            })
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove(video)
                        },
                        enabled = !operationInProgress
                    )
                }
            }
        }
        if (kind == LibraryKind.HISTORY) {
            // 远端历史以 -1 表示已看完，不能把它当作“尚未记录进度”。
            val watchedToEnd = video.progressSeconds < 0 ||
                (video.durationSeconds > 0 && video.progressSeconds >= video.durationSeconds)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        watchedToEnd -> "已看完"
                        video.progressSeconds > 0 -> "观看至 ${formatDuration(video.progressSeconds)}"
                        else -> "尚未记录进度"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onOpen(video) }) {
                    Text(when {
                        watchedToEnd -> "重新观看"
                        video.progressSeconds > 0 -> "继续观看"
                        else -> "观看视频"
                    })
                }
            }
            if (video.durationSeconds > 0) {
                LinearProgressIndicator(
                    progress = {
                        if (watchedToEnd) 1f
                        else (video.progressSeconds.toFloat() / video.durationSeconds).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
