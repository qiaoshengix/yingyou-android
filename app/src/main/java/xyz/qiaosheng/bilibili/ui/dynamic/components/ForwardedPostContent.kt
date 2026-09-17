package xyz.qiaosheng.bilibili.ui.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.model.dynamic.DynamicForwardedPost

/** 转发内容保留独立背景层级，媒体仍复用动态的展示组件。 */
@Composable
internal fun ForwardedPostContent(
    forwardedPost: DynamicForwardedPost,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        if (forwardedPost.authorName.isNotBlank()) {
            Text(
                text = "@${forwardedPost.authorName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (forwardedPost.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(forwardedPost.text, style = MaterialTheme.typography.bodyMedium)
        }
        forwardedPost.media?.let { media ->
            DynamicMediaContent(
                media = media,
                onNavigateToVideo = onNavigateToVideo,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
