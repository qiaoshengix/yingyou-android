package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 将播放器事件和进度轮询转换为 Compose 可以观察的状态。 */
internal data class PlayerUiSnapshot(
    val isPlaying: Boolean,
    val playbackState: Int,
    val currentPositionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long
)

@Composable
internal fun rememberPlayerUiSnapshot(player: Player): PlayerUiSnapshot {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var currentPositionMs by remember(player) {
        mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
    }
    var durationMs by remember(player) {
        mutableLongStateOf(player.safeDuration())
    }
    var bufferedPositionMs by remember(player) {
        mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L))
    }

    fun readPlayerState() {
        isPlaying = player.isPlaying
        playbackState = player.playbackState
        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.safeDuration()
        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                readPlayerState()
            }
        }

        player.addListener(listener)
        readPlayerState()

        onDispose {
            player.removeListener(listener)
        }
    }

    // 播放进度不会连续触发 Player.Listener，因此单独轮询，离开组合时自动取消。
    LaunchedEffect(player, isPlaying) {
        while (isActive) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            delay((if (isPlaying) 250L else 1_000L).milliseconds)
        }
    }

    return PlayerUiSnapshot(
        isPlaying = isPlaying,
        playbackState = playbackState,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs
    )
}

private fun Player.safeDuration(): Long =
    duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
