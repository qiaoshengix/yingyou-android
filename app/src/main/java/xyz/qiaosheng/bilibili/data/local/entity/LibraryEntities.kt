package xyz.qiaosheng.bilibili.data.local.entity

import androidx.room.Entity
import xyz.qiaosheng.bilibili.model.library.LibraryVideo

/** 每个账号/列表/收藏夹独立主键；0 号账号仅用于访客观看历史，绝不自动并入登录账号。 */
@Entity(tableName = "library_entries", primaryKeys = ["accountId", "kind", "folderId", "bvid"])
data class LibraryEntryEntity(
    val accountId: Long,
    val kind: String,
    val folderId: Long,
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val ownerName: String,
    val durationSeconds: Long,
    val progressSeconds: Long,
    val viewedAt: Long,
    val sortAt: Long,
    val modifiedAt: Long,
    val deleted: Boolean = false,
    val pending: Boolean = false,
    val generation: String = ""
) {
    fun video() = LibraryVideo(bvid, aid, cid, title, coverUrl, ownerName, durationSeconds,
        progressSeconds, viewedAt, folderId.takeIf { it > 0 })
}

@Entity(tableName = "library_folders", primaryKeys = ["accountId", "folderId"])
data class FavoriteFolderEntity(val accountId: Long, val folderId: Long, val title: String, val mediaCount: Int)

/** 一个资源只保留最后意图；mutationId 防止旧网络应答删除后来排队的新意图。凭据不写入队列。 */
@Entity(tableName = "library_outbox", primaryKeys = ["accountId", "kind", "folderId", "bvid"])
data class LibraryOutboxEntity(
    val accountId: Long,
    val kind: String,
    val folderId: Long,
    val bvid: String,
    val mutationId: String,
    val desired: Boolean,
    val aid: Long,
    val cid: Long,
    val progressSeconds: Long,
    val completed: Boolean,
    val createdAt: Long,
    val attempts: Int = 0,
    val state: String = "PENDING",
    val error: String? = null
)

@Entity(tableName = "watch_progress", primaryKeys = ["accountId", "bvid", "cid"])
data class WatchProgressEntity(
    val accountId: Long, val bvid: String, val cid: Long,
    val positionSeconds: Long, val completed: Boolean, val updatedAt: Long
)
