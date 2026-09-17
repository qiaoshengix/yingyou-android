package xyz.qiaosheng.bilibili.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.model.video.VideoPageData

/** 播放请求携带发起时的账号与分 P，后台服务不会把旧视频记到后来登录的账号。 */
object PlaybackHistoryMetadata {
    const val ACCOUNT_ID = "history.accountId"
    const val CID = "history.cid"
    private const val AID = "history.aid"
    private const val TITLE = "history.title"
    private const val COVER = "history.cover"
    private const val OWNER = "history.owner"
    private const val DURATION = "history.duration"

    fun attach(item: MediaItem, page: VideoPageData, accountId: Long): MediaItem {
        // 保留清晰度、音频 URI 和离线缓存键；两类播放共用记录协议。
        val extras = Bundle(item.requestMetadata.extras ?: Bundle()).apply {
            putLong(ACCOUNT_ID, accountId)
            putLong(AID, page.aid)
            putLong(CID, page.cid)
            putString(TITLE, page.detail.title)
            putString(COVER, page.detail.coverUrl)
            putString(OWNER, page.detail.owner.name)
            putLong(DURATION, page.detail.duration.toLong())
        }
        return item.buildUpon().setRequestMetadata(
            item.requestMetadata.buildUpon().setExtras(extras).build()
        ).build()
    }

    fun read(item: MediaItem?): WatchTarget? {
        item ?: return null
        val extras = item.requestMetadata.extras ?: return null
        if (!extras.containsKey(ACCOUNT_ID) || item.mediaId.isBlank()) return null
        val cid = extras.getLong(CID)
        val aid = extras.getLong(AID)
        if (cid <= 0L || aid <= 0L) return null
        return WatchTarget(
            accountId = extras.getLong(ACCOUNT_ID),
            video = LibraryVideo(
                bvid = item.mediaId,
                aid = aid,
                cid = cid,
                title = extras.getString(TITLE).orEmpty(),
                coverUrl = extras.getString(COVER).orEmpty(),
                ownerName = extras.getString(OWNER).orEmpty(),
                durationSeconds = extras.getLong(DURATION)
            )
        )
    }
}

data class WatchTarget(val accountId: Long, val video: LibraryVideo) {
    val key: String get() = "$accountId:${video.bvid}:${video.cid}"
}
