package xyz.qiaosheng.bilibili.ui.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.core.format.formatPublishTime
import xyz.qiaosheng.bilibili.model.video.VideoDetail

@Composable
internal fun VideoInfoSection(video: VideoDetail) {
    var expanded by remember(video.bvid) { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${formatCount(video.playCount)}播放 · ${formatCount(video.danmakuCount)}弹幕 · ${formatPublishTime(video.pubdate)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "BV号 ${video.bvid}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (video.desc.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.desc,
                modifier = Modifier.clickable { expanded = !expanded },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
}
