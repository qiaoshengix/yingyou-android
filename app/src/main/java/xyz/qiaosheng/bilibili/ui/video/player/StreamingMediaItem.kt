package xyz.qiaosheng.bilibili.ui.video.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import xyz.qiaosheng.bilibili.data.remote.dto.DashStream
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.playback.EXTRA_AUDIO_URI
import xyz.qiaosheng.bilibili.playback.EXTRA_QUALITY_ID
import xyz.qiaosheng.bilibili.playback.PlaybackHistoryMetadata

/** 音视频地址、展示元数据与历史目标账号一起交给 Service，页面不拥有 ExoPlayer。 */
internal fun streamingMediaItem(
    page: VideoPageData, video: DashStream, audio: DashStream, accountId: Long
): MediaItem = PlaybackHistoryMetadata.attach(
    MediaItem.Builder()
        .setMediaId(page.detail.bvid)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder()
            .setMediaUri(video.baseUrl.toUri())
            .setExtras(Bundle().apply {
                putString(EXTRA_AUDIO_URI, audio.baseUrl)
                putInt(EXTRA_QUALITY_ID, video.id)
            }).build())
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(page.detail.title).setArtist(page.detail.owner.name)
            .setArtworkUri(page.detail.coverUrl.toUri()).build())
        .build(), page, accountId
)

// 同清晰度优先选择兼容性较广的编码，再选择较高带宽。
internal fun List<DashStream>.preferredVideoStream(): DashStream = minWith(
    compareBy<DashStream> {
        when {
            it.codecs.startsWith("avc", true) -> 0
            it.codecs.startsWith("hev", true) || it.codecs.startsWith("hvc", true) -> 1
            it.codecs.startsWith("av01", true) -> 2
            else -> 3
        }
    }.thenByDescending { it.bandwidth }
)
