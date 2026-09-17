package xyz.qiaosheng.bilibili.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository

/**
 * 跟随真实 Player 生命周期记录，而不是跟随详情页重组。
 * 后台听也会每 10 秒落盘；暂停、跳转、结束和服务释放会补最后一个进度点。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackHistoryRecorder(
    private val player: Player,
    private val repository: LibraryRepository,
    private val errorReporter: ErrorReporter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // 保持跨视频的写入顺序；不能用 conflated，否则切视频时旧视频最后一帧可能被丢掉。
    private val checkpoints = Channel<Checkpoint>(Channel.UNLIMITED)
    private var startedKey: String? = null
    private var lastCheckpoint: Checkpoint? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startedKey = PlaybackHistoryMetadata.read(player.currentMediaItem)?.key
            capture()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) capture(completed = true)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // 此时 currentPosition 已经属于新媒体/新位置，必须先使用旧快照补写。
            capture(oldPosition.mediaItem, oldPosition.positionMs)
            if (PlaybackHistoryMetadata.read(oldPosition.mediaItem)?.key !=
                PlaybackHistoryMetadata.read(newPosition.mediaItem)?.key) {
                startedKey = null
            }
            if (player.isPlaying) startedKey = PlaybackHistoryMetadata.read(newPosition.mediaItem)?.key
            capture(newPosition.mediaItem, newPosition.positionMs)
        }
    }

    private val writer = scope.launch(Dispatchers.IO) {
        try {
            for (checkpoint in checkpoints) {
                try {
                    repository.recordWatch(
                        checkpoint.target.accountId,
                        checkpoint.target.video,
                        checkpoint.positionSeconds,
                        checkpoint.completed
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // 历史存储错误不能停止播放器；后续进度点仍可尝试写入。
                    errorReporter.message("保存观看进度失败", error)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    private val ticker = scope.launch {
        while (isActive) {
            delay(10_000L)
            if (player.isPlaying) capture()
        }
    }

    init {
        player.addListener(listener)
        if (player.isPlaying) startedKey = PlaybackHistoryMetadata.read(player.currentMediaItem)?.key
    }

    fun flush() = capture()

    fun close() {
        capture()
        player.removeListener(listener)
        ticker.cancel()
        // 不阻塞主线程，也不立即取消 IO：先排空已经取得的进度快照，再释放协程。
        checkpoints.close()
    }

    private fun capture(
        item: MediaItem? = player.currentMediaItem,
        positionMs: Long = player.currentPosition,
        completed: Boolean = player.playbackState == Player.STATE_ENDED
    ) {
        val target = PlaybackHistoryMetadata.read(item) ?: return
        if (target.key != startedKey || (positionMs <= 0L && !completed)) return
        val seconds = positionMs.coerceAtLeast(0L) / 1000L
        val finished = completed || (target.video.durationSeconds > 0L && seconds >= target.video.durationSeconds)
        val checkpoint = Checkpoint(target, seconds, finished)
        if (checkpoint == lastCheckpoint) return
        lastCheckpoint = checkpoint
        checkpoints.trySend(checkpoint)
    }

    private data class Checkpoint(
        val target: WatchTarget,
        val positionSeconds: Long,
        val completed: Boolean
    )
}
