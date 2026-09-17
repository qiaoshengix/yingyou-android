package xyz.qiaosheng.bilibili.ui.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.model.video.VideoDetail
import xyz.qiaosheng.bilibili.ui.video.VideoActionsState

@Composable
internal fun VideoActionBar(
    video: VideoDetail,
    actions: VideoActionsState,
    offline: Boolean,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionItem(
            Icons.Default.ThumbUp,
            formatCount(video.likeCount),
            if (actions.status.liked) "已点赞" else "点赞",
            actions.status.liked,
            !actions.busy,
            onLike
        )
        ActionItem(
            Icons.Default.MonetizationOn,
            formatCount(video.coinCount),
            "投币",
            enabled = false
        )
        ActionItem(
            Icons.Default.Star, formatCount(video.favoriteCount),
            if (actions.status.favoriteFolderIds.isNotEmpty()) "已收藏" else "收藏",
            actions.status.favoriteFolderIds.isNotEmpty(), !actions.busy, onFavorite
        )
        ActionItem(
            Icons.Default.Download, "", if (offline) "已缓存" else "缓存",
            offline, !offline && !actions.busy, onDownload
        )
        ActionItem(Icons.Default.Share, formatCount(video.shareCount), "分享", onClick = onShare)
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    count: String,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp)
    ) {
        Icon(
            icon, contentDescription = if (enabled) label else "$label，当前不可用",
            modifier = Modifier.size(24.dp),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
        if (count.isNotBlank()) Text(count, style = MaterialTheme.typography.labelSmall)
    }
}
