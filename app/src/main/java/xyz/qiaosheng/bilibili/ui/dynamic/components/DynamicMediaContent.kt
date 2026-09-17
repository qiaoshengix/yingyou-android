package xyz.qiaosheng.bilibili.ui.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.qiaosheng.bilibili.model.dynamic.DynamicMedia

/** 原始动态与转发动态共用媒体展示，保持同一种媒体的呈现方式一致。 */
@Composable
internal fun DynamicMediaContent(
    media: DynamicMedia,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (media) {
        is DynamicMedia.Video -> VideoDynamicContent(media, onNavigateToVideo, modifier)
        is DynamicMedia.Images -> DynamicImageGrid(media.urls, modifier)
        is DynamicMedia.Article -> ArticleDynamicContent(media, modifier)
    }
}

@Composable
private fun VideoDynamicContent(
    video: DynamicMedia.Video,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onNavigateToVideo(video.bvid) }
    ) {
        Box {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val statText = listOf(
                video.playCountText.takeIf(String::isNotBlank)?.let { "${it}播放" },
                video.danmakuCountText.takeIf(String::isNotBlank)?.let { "${it}弹幕" }
            ).filterNotNull().joinToString("  ")
            if (statText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        statText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicImageGrid(urls: List<String>, modifier: Modifier = Modifier) {
    val visibleUrls = urls.take(9)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (visibleUrls.size == 1) {
            AsyncImage(
                model = visibleUrls.first(),
                contentDescription = "动态图片",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            visibleUrls.chunked(3).forEach { rowUrls ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowUrls.forEach { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "动态图片",
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    repeat(3 - rowUrls.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ArticleDynamicContent(article: DynamicMedia.Article, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            article.coverUrls.firstOrNull()?.let { coverUrl ->
                AsyncImage(
                    model = coverUrl,
                    contentDescription = article.title,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (article.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = article.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
