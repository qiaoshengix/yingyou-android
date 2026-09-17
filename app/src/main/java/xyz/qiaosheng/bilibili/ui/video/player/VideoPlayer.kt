package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import xyz.qiaosheng.bilibili.ui.video.DanmakuUiState
import xyz.qiaosheng.bilibili.ui.video.danmaku.DanmakuOverlay

/** 只绑定播放画面；播放器的创建和释放归 PlaybackService 管理。 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: Player?,
    qualities: List<VideoQuality>,
    selectedQualityId: Int,
    isFullScreen: Boolean,
    onQualitySelected: (Int) -> Unit,
    onFullScreenChange: (Boolean) -> Unit,
    isAudioOnly: Boolean,
    danmaku: DanmakuUiState,
    onToggleBackgroundAudio: () -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onRetryDanmaku: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (player == null) {
            CircularProgressIndicator()
            // 控制器重连时也保留全屏返回入口，不能让加载状态困住用户。
            if (isFullScreen) {
                IconButton(
                    onClick = { onFullScreenChange(false) },
                    modifier = Modifier.align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出全屏并返回", tint = Color.White)
                }
            }
            return@Box
        }

        AndroidView(
            factory = { context ->
                PlayerView(context).also { playerView ->
                    playerView.player = player
                    playerView.useController = false
                    playerView.setEnableComposeSurfaceSyncWorkaround(true)
                    playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = player },
            // 页面退出或切换全屏时只解绑 Surface，后台播放可以继续。
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Canvas 不拦截触摸；留出上下控制区，时钟始终来自同一个播放器。
        DanmakuOverlay(
            player = player,
            items = danmaku.items,
            enabled = danmaku.preferencesReady && danmaku.enabled && !isAudioOnly &&
                danmaku.unavailableReason == null,
            modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 52.dp),
        )

        PlayerControls(
            player = player,
            qualities = qualities,
            selectedQualityId = selectedQualityId,
            isFullScreen = isFullScreen,
            onQualitySelected = onQualitySelected,
            onFullScreenChange = onFullScreenChange,
            isAudioOnly = isAudioOnly,
            danmaku = danmaku,
            onToggleBackgroundAudio = onToggleBackgroundAudio,
            onDanmakuEnabledChange = onDanmakuEnabledChange,
            onRetryDanmaku = onRetryDanmaku,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun VideoPlayerLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
