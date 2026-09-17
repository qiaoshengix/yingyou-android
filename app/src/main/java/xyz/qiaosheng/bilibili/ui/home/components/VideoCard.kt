package xyz.qiaosheng.bilibili.ui.home.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.qiaosheng.bilibili.core.format.formatCount
import xyz.qiaosheng.bilibili.core.format.formatDuration
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

@Composable
fun VideoCard(
    video: RecommendVideo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()  // ✅ 关键：让 Column 填满 Card 宽度
        ) {
            // ========== 封面区域 ==========
            Box {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration.toLong()),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // ========== 内容区域 ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()  // ✅ 关键：让内层 Column 也填满宽度
                    .padding(10.dp)
            ) {
                // 标题
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ========== 底部信息栏 ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),  // ✅ 填满 Column 宽度
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween  // ✅ 两端对齐
                ) {
                    // 左侧：头像 + 名字
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = video.ownerAvatar,
                            contentDescription = video.ownerName,
                            modifier = Modifier
                                .width(38.dp)
                                .aspectRatio(1f / 1f)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = video.ownerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,  // ✅ 防止用户名过长
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 右侧：播放量（现在会跑到最右边）
                    Text(
                        text = "${formatCount(video.playCount)}播放",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VideoCardPreview() {
    VideoCard(
        RecommendVideo(
            bvid = "BV1uQ4y1Z7gA",
            title = "OkHttp&Retrofit大家都在用，原理很懵懂？2021玩转网络框架，你想要的都在这里了！",
            coverUrl = "https://i0.hdslb.com/bfs/bangumi/image/8abdbfbc1982f1d003b4e69e2011b502bc9d4ea9.png",
            duration = 360000,
            ownerName = "迷影社",
            ownerAvatar = "https://i0.hdslb.com/bfs/face/c6d1a6222df921bcd8a7fc1c39efa35eb29ef163.jpg",
            playCount = 51413675,
            danmakuCount = 1944110
        ),
        onClick = {}
    )
}
