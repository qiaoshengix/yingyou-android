package xyz.qiaosheng.bilibili.ui.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.model.video.VideoOwner

@Composable
internal fun VideoOwnerSection(owner: VideoOwner) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = owner.avatarUrl,
            contentDescription = owner.name,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = owner.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "粉丝 ${formatCount(owner.fansCount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { /* TODO: 关注/取关 */ },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(
                if (owner.following) "已关注" else "+ 关注",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
