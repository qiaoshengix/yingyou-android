package xyz.qiaosheng.bilibili.offline

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineMediaSourceFactory @Inject constructor(
    private val offlineCache: OfflineCache,
    private val authSession: AuthSessionManager
) {
    fun createMediaSource(item: MediaItem): MediaSource {
        val extras = item.requestMetadata.extras ?: throw IOException("离线媒体信息缺失")
        val owner = extras.getLong(OfflineMediaContract.ACCOUNT_ID, -1L)
        val cid = extras.getLong(OfflineMediaContract.CID, -1L)
        if (!OfflineMediaContract.isOffline(item) || owner < 0 || cid <= 0 ||
            owner != authSession.authState.value.offlineAccountId()) throw IOException("当前账号无法播放此离线缓存")
        val prefix = OfflineKeys.id(owner, item.mediaId, cid) + ":"
        val videoKey = extras.getString(OfflineMediaContract.VIDEO_KEY)
        val audioKey = extras.getString(OfflineMediaContract.AUDIO_KEY)
        if (videoKey == null || audioKey == null || !videoKey.startsWith(prefix + "video:") ||
            !audioKey.startsWith(prefix + "audio:") || !offlineCache.isComplete(videoKey) ||
            !offlineCache.isComplete(audioKey)) throw IOException("离线音视频缓存不完整，请重新缓存")

        val local = offlineCache.localOnlyFactory()
        val factory = ProgressiveMediaSource.Factory(DataSource.Factory {
            AccountGuardedDataSource(local.createDataSource()) { authSession.authState.value.offlineAccountId() }
        })
        fun source(key: String, audio: Boolean): MediaSource = factory.createMediaSource(
            item.buildUpon()
                .setUri("offline://media/${android.net.Uri.encode(key)}".toUri())
                .setCustomCacheKey(key)
                .setMimeType(if (audio) MimeTypes.AUDIO_MP4 else MimeTypes.VIDEO_MP4)
                .build()
        )
        return MergingMediaSource(source(videoKey, false), source(audioKey, true))
    }
}
