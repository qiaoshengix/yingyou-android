package xyz.qiaosheng.bilibili.ui.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.core.format.formatRelativeTime
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPost

/** 单条动态只负责展示数据；视频导航通过回调交给页面处理。 */
@Composable
internal fun DynamicPostCard(post: DynamicPost, onNavigateToVideo: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 16.dp)
    ) {
        DynamicAuthorHeader(post)
        if (post.text.isNotBlank()) {
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 10.dp)
            )
        }
        post.media?.let { media ->
            DynamicMediaContent(
                media = media,
                onNavigateToVideo = onNavigateToVideo,
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp)
            )
        }
        post.forwardedPost?.let { forwardedPost ->
            ForwardedPostContent(
                forwardedPost = forwardedPost,
                onNavigateToVideo = onNavigateToVideo,
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp)
            )
        }
        DynamicStats(post)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun DynamicAuthorHeader(post: DynamicPost) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = post.authorAvatar,
            contentDescription = post.authorName,
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = post.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val meta = listOf(formatRelativeTime(post.publishTimestamp), post.publishAction)
                .filter(String::isNotBlank).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DynamicStats(post: DynamicPost) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DynamicStat(Icons.Default.Repeat, post.forwardCount, "转发")
        DynamicStat(Icons.Outlined.ChatBubbleOutline, post.commentCount, "评论")
        DynamicStat(
            icon = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            count = post.likeCount,
            emptyLabel = "点赞",
            tint = if (post.isLiked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DynamicStat(
    icon: ImageVector,
    count: Long,
    emptyLabel: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (count > 0) formatCount(count) else emptyLabel,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}
