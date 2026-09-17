package xyz.qiaosheng.bilibili.offline

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity

/** 所有离线信息放进可跨 MediaSession 传输的 requestMetadata；不携带远程音视频 URI。 */
object OfflineMediaContract {
    const val ONLY = "offline.only"
    const val ACCOUNT_ID = "offline.accountId"
    const val VIDEO_KEY = "offline.videoKey"
    const val AUDIO_KEY = "offline.audioKey"
    const val CID = "offline.cid"
    const val QUALITY_ID = "offline.qualityId"

    fun isOffline(item: MediaItem): Boolean = item.requestMetadata.extras?.getBoolean(ONLY) == true

    internal fun mediaItem(page: VideoPageData, row: OfflineVideoEntity): MediaItem {
        val uri = "offline://media/${android.net.Uri.encode(row.videoKey)}".toUri()
        return MediaItem.Builder()
            .setMediaId(row.bvid)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(Bundle().apply {
                putBoolean(ONLY, true)
                putLong(ACCOUNT_ID, row.accountId)
                putString(VIDEO_KEY, row.videoKey)
                putString(AUDIO_KEY, row.audioKey)
                putLong(CID, row.cid)
                putInt(QUALITY_ID, row.qualityId)
            }).build())
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(page.detail.title)
                .setArtist(page.detail.owner.name)
                .setArtworkUri(page.detail.coverUrl.takeIf { it.isNotBlank() }?.toUri())
                .build())
            .build()
    }
}
