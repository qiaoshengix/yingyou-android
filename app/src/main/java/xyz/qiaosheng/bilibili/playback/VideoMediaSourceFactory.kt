package xyz.qiaosheng.bilibili.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import xyz.qiaosheng.bilibili.offline.OfflineMediaContract
import xyz.qiaosheng.bilibili.offline.OfflineMediaSourceFactory

const val EXTRA_AUDIO_URI = "playback.audioUri"
const val EXTRA_QUALITY_ID = "playback.qualityId"

/** 音视频流只在 Service 内合并，页面只发送可跨 MediaSession 传递的 MediaItem。 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val offlineFactory: OfflineMediaSourceFactory
) : MediaSource.Factory by delegate {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        // 离线请求使用没有 upstream 的缓存源，缺片时明确失败，不退回网络。
        if (OfflineMediaContract.isOffline(mediaItem)) {
            return offlineFactory.createMediaSource(mediaItem)
        }
        val videoSource = delegate.createMediaSource(mediaItem)
        val audioUri = mediaItem.requestMetadata.extras?.getString(EXTRA_AUDIO_URI)
            ?: return videoSource
        val audioSource = delegate.createMediaSource(MediaItem.fromUri(audioUri))
        // 主视频源保留 mediaId、标题及清晰度信息，供控制器和通知读取。
        return MergingMediaSource(videoSource, audioSource)
    }
}
