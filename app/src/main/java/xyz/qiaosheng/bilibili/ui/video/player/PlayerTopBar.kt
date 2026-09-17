package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.ui.video.DanmakuUiState

/** 顶栏集中放置播放模式操作；返回只退出全屏，开关的持久化由 ViewModel 负责。 */
@Composable
internal fun PlayerTopBar(
    isFullScreen: Boolean,
    isAudioOnly: Boolean,
    danmaku: DanmakuUiState,
    onExitFullScreen: () -> Unit,
    onToggleBackgroundAudio: () -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onRetryDanmaku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isFullScreen) {
                IconButton(onClick = onExitFullScreen) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出全屏并返回", tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleBackgroundAudio) {
                Icon(Icons.Default.Headphones, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    if (isAudioOnly) "恢复画面" else "后台听",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // 视频固定使用深色遮罩，提亮当前主题色，避免浅色主题的深色 primary 难以辨认。
            val switchColor = if (danmaku.enabled) {
                lerp(MaterialTheme.colorScheme.primary, Color.White, 0.5f)
            } else Color.White
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .toggleable(
                        value = danmaku.enabled,
                        enabled = danmaku.preferencesReady,
                        role = Role.Switch,
                        onValueChange = onDanmakuEnabledChange,
                    )
                    .semantics {
                        contentDescription = "弹幕开关"
                        stateDescription = if (danmaku.enabled) "已开启" else "已关闭"
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Subtitles, null, tint = switchColor, modifier = Modifier.size(20.dp))
                Text(
                    if (danmaku.enabled) "弹幕开" else "弹幕关",
                    color = switchColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // 错误不影响视频播放；仅在控制条可见时提示，并允许用户主动重试。
        val message = danmaku.errorMessage ?: if (danmaku.enabled) {
            danmaku.unavailableReason ?: if (danmaku.loading) "弹幕加载中…" else null
        } else null
        if (message != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f, fill = false),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (danmaku.errorMessage != null) {
                    TextButton(onClick = onRetryDanmaku) { Text("重试弹幕", color = Color.White) }
                }
            }
        }
    }
}
