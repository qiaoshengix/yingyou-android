package xyz.qiaosheng.bilibili.ui.mine.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.model.profile.UserProfile

/** 个人资料展示：头像、签名、统计和等级颜色统一在卡片内维护。 */
@Composable
internal fun UserProfileCard(profile: UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.name,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                profile.sign.ifEmpty { "这个人很懒什么都没写" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("关注", profile.followingCount)
                StatItem("粉丝", profile.followerCount)
                StatItem("硬币", profile.coins)
            }
        }
        Box(
            modifier = Modifier
                .background(levelColor(profile.level), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                "Lv${profile.level}", color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(value.toLong()), style = MaterialTheme.typography.titleSmall)
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun levelColor(level: Int): Color = when (level) {
    0, 1 -> Color(0xFFBFBFBF)
    2 -> Color(0xFF95DDB2)
    3 -> Color(0xFF92D1E5)
    4 -> Color(0xFFFFB37C)
    5 -> Color(0xFFFF6C9B)
    6 -> Color(0xFFFF0000)
    else -> Color(0xFFBFBFBF)
}
