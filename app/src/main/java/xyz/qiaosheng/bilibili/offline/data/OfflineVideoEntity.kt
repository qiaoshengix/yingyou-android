package xyz.qiaosheng.bilibili.offline.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 元数据与 Media3 的 DownloadIndex 分开保存，账号/分P/流身份不依赖临时 CDN URL。 */
@Entity(tableName = "offline_videos", indices = [Index(value = ["accountId", "bvid", "cid"], unique = true)])
data class OfflineVideoEntity(
    @PrimaryKey val id: String,
    val accountId: Long,
    val bvid: String,
    val cid: Long,
    val pageJson: String,
    val qualityId: Int,
    val qualityLabel: String,
    val videoCodec: String,
    val videoBandwidth: Int,
    val audioId: Int,
    val audioCodec: String,
    val audioBandwidth: Int,
    val videoUrl: String,
    val audioUrl: String,
    val videoKey: String,
    val audioKey: String,
    val createdAt: Long,
    val status: String = "QUEUED",
    val userPaused: Boolean = false,
    val deleting: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val errorMessage: String? = null
) {
    val videoRequestId: String get() = "$id:video"
    val audioRequestId: String get() = "$id:audio"
}
