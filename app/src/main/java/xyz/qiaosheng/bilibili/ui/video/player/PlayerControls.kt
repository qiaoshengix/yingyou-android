package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import xyz.qiaosheng.bilibili.core.format.formatPlayerTime
import xyz.qiaosheng.bilibili.ui.video.DanmakuUiState

@Composable
internal fun PlayerControls(
    player: Player,
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
    val snapshot = rememberPlayerUiSnapshot(player)
    val shouldShowPlayButton = Util.shouldShowPlayButton(player)
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    // 拖动期间先展示预览进度，松手后再向播放器提交 seek。
    var draggedPositionMs by remember { mutableStateOf<Long?>(null) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    fun markInteraction() {
        controlsVisible = true
        interactionVersion++
    }

    // 每次交互都会重置隐藏倒计时，暂停时保持控制条可见。
    LaunchedEffect(controlsVisible, snapshot.isPlaying, interactionVersion, draggedPositionMs, qualityMenuExpanded) {
        if (controlsVisible && snapshot.isPlaying && draggedPositionMs == null && !qualityMenuExpanded) {
            delay(3_000L.milliseconds)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures {
                controlsVisible = !controlsVisible
                if (controlsVisible) interactionVersion++
            }
        }
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            ) {
                PlayerTopBar(
                    isFullScreen = isFullScreen,
                    isAudioOnly = isAudioOnly,
                    danmaku = danmaku,
                    onExitFullScreen = { markInteraction(); onFullScreenChange(false) },
                    onToggleBackgroundAudio = { markInteraction(); onToggleBackgroundAudio() },
                    onDanmakuEnabledChange = { markInteraction(); onDanmakuEnabledChange(it) },
                    onRetryDanmaku = { markInteraction(); onRetryDanmaku() },
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        // 竖屏已由 Scaffold 避开刘海，仅沉浸式全屏需要再次处理。
                        .then(if (isFullScreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
                        .padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = {
                        markInteraction()
                        Util.handlePlayPauseButtonAction(player)
                    },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = if (shouldShowPlayButton) {
                            Icons.Default.PlayCircle
                        } else {
                            Icons.Default.PauseCircle
                        },
                        contentDescription = if (shouldShowPlayButton) "播放" else "暂停",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(if (isFullScreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            markInteraction()
                            Util.handlePlayPauseButtonAction(player)
                        }
                    ) {
                        Icon(
                            imageVector = if (shouldShowPlayButton) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Pause
                            },
                            contentDescription = if (shouldShowPlayButton) "播放" else "暂停",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = formatPlayerTime(
                            draggedPositionMs ?: snapshot.currentPositionMs
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Slider(
                        value = (draggedPositionMs ?: snapshot.currentPositionMs)
                            .coerceIn(0L, snapshot.durationMs.coerceAtLeast(1L))
                            .toFloat(),
                        onValueChange = { value ->
                            markInteraction()
                            draggedPositionMs = value.toLong()
                        },
                        onValueChangeFinished = {
                            draggedPositionMs?.let(player::seekTo)
                            draggedPositionMs = null
                            markInteraction()
                        },
                        valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        text = formatPlayerTime(snapshot.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )

                    QualityMenuButton(
                        qualities = qualities,
                        selectedQualityId = selectedQualityId,
                        onQualitySelected = onQualitySelected,
                        onUserInteraction = ::markInteraction,
                        onExpandedChange = { qualityMenuExpanded = it },
                    )

                    IconButton(
                        onClick = {
                            markInteraction()
                            onFullScreenChange(!isFullScreen)
                        }
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },
                            contentDescription = if (isFullScreen) "退出全屏" else "全屏",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
