package xyz.qiaosheng.bilibili.data.remote

import com.google.gson.Gson
import javax.inject.Inject
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.local.entity.LibraryOutboxEntity
import xyz.qiaosheng.bilibili.data.remote.api.LibraryApiService
import xyz.qiaosheng.bilibili.data.remote.dto.*
import xyz.qiaosheng.bilibili.data.sync.LibraryCredentials
import xyz.qiaosheng.bilibili.model.library.LibraryVideo

data class RemoteLibraryPage(val videos: List<LibraryVideo>, val nextCursor: String? = null, val hasMore: Boolean = false)

interface LibraryRemoteDataSource {
    suspend fun history(session: LibraryCredentials, cursor: String?): RemoteLibraryPage
    suspend fun folders(session: LibraryCredentials, aid: Long? = null): List<FavoriteFolderDto>
    suspend fun favorites(session: LibraryCredentials, folderId: Long, cursor: String?): RemoteLibraryPage
    suspend fun recentLikes(session: LibraryCredentials): RemoteLibraryPage
    suspend fun liked(session: LibraryCredentials, bvid: String): Boolean
    suspend fun resolve(session: LibraryCredentials, video: LibraryVideo): LibraryVideo
    suspend fun submit(session: LibraryCredentials, operation: LibraryOutboxEntity)
}

class RetrofitLibraryRemoteDataSource @Inject constructor(private val api: LibraryApiService, private val gson: Gson) : LibraryRemoteDataSource {
    override suspend fun history(session: LibraryCredentials, cursor: String?): RemoteLibraryPage {
        val parts = cursor?.split('|')
        val response = api.history(session.cookieHeader, parts?.getOrNull(0)?.toLongOrNull() ?: 0,
            parts?.getOrNull(1)?.toLongOrNull() ?: 0, parts?.getOrNull(2).orEmpty())
        checkCode(response.code, response.message)
        val data = response.data ?: throw InvalidResponseException("历史接口缺少 data")
        val rows = data.list ?: throw InvalidResponseException("历史接口缺少 data.list")
        val videos = rows.mapNotNull { row ->
            val target = row.history ?: return@mapNotNull null
            val bvid = target.bvid?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (target.business != "archive") return@mapNotNull null
            LibraryVideo(bvid, target.oid, target.cid, row.title.orEmpty(), row.cover.orEmpty().toHttps(),
                row.authorName.orEmpty(), row.duration, row.progress, row.viewAt)
        }
        val next = data.cursor?.let { "${it.max}|${it.viewAt}|${it.business}" }
        // 空页或不前进的游标都结束分页，避免服务端异常导致无限重复加载。
        val more = rows.isNotEmpty() && data.cursor?.max != 0L && next != cursor && next != null
        return RemoteLibraryPage(videos, next.takeIf { more }, more)
    }

    override suspend fun folders(session: LibraryCredentials, aid: Long?): List<FavoriteFolderDto> {
        val value = data(api.folders(session.cookieHeader, session.accountId, aid))
        if (value.count == null || value.count > 0 && value.list == null) throw InvalidResponseException("收藏夹接口返回不完整")
        if (aid != null && value.list.orEmpty().any { it.favState != 0 && it.favState != 1 })
            throw InvalidResponseException("收藏夹接口缺少视频归属状态")
        return value.list.orEmpty().filter { it.id > 0 }
    }

    override suspend fun favorites(session: LibraryCredentials, folderId: Long, cursor: String?): RemoteLibraryPage {
        val page = cursor?.toIntOrNull() ?: 1
        val value = data(api.favorites(session.cookieHeader, folderId, page))
        if (value.info?.id != folderId) throw InvalidResponseException("收藏夹内容缺少匹配的 info")
        val hasMore = value.hasMore ?: throw InvalidResponseException("收藏夹内容缺少分页状态")
        val videos = value.medias.orEmpty().mapNotNull { row ->
            if (row.type != 2) return@mapNotNull null
            val bvid = (row.bvid ?: row.bvId)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            LibraryVideo(bvid, row.id, title = row.title.orEmpty(), coverUrl = row.cover.orEmpty().toHttps(),
                ownerName = row.upper?.name.orEmpty(), durationSeconds = row.duration, viewedAt = row.favTime, folderId = folderId)
        }
        if (hasMore && value.medias.isNullOrEmpty()) throw InvalidResponseException("收藏分页未返回内容")
        return RemoteLibraryPage(videos, (page + 1).toString().takeIf { hasMore }, hasMore)
    }

    override suspend fun recentLikes(session: LibraryCredentials): RemoteLibraryPage {
        val payload = data(api.recentLikes(session.cookieHeader, session.accountId))
        val rows = when {
            payload.isJsonArray -> payload.asJsonArray
            payload.isJsonObject && payload.asJsonObject.get("list")?.isJsonArray == true -> payload.asJsonObject.getAsJsonArray("list")
            else -> throw InvalidResponseException("点赞接口缺少 list")
        }
        return RemoteLibraryPage(rows.mapNotNull { element -> gson.fromJson(element, LibraryVideoDto::class.java).video() })
    }

    override suspend fun liked(session: LibraryCredentials, bvid: String): Boolean = when (data(api.hasLike(session.cookieHeader, bvid))) {
        0 -> false
        1 -> true
        else -> throw InvalidResponseException("点赞状态接口返回未知状态")
    }

    override suspend fun resolve(session: LibraryCredentials, video: LibraryVideo): LibraryVideo {
        if (video.aid > 0) return video
        val raw = data(api.detail(session.cookieHeader, video.bvid))
        return video.copy(aid = raw.aid, cid = video.cid.takeIf { it > 0 } ?: raw.cid)
    }

    override suspend fun submit(session: LibraryCredentials, operation: LibraryOutboxEntity) {
        if (session.csrf.isBlank()) throw LoginRequiredException()
        when (operation.kind) {
            "LIKES" -> {
                // 重试是设置目标状态；先核实状态，避免把重复点赞当作永久失败。
                if (liked(session, operation.bvid) != operation.desired) {
                    val reply = api.like(session.cookieHeader, operation.bvid, if (operation.desired) 1 else 2, session.csrf)
                    if (reply.code != 0 && !(operation.desired && reply.code == 65006)) checkCode(reply.code, reply.message)
                }
            }
            "FAVORITES" -> {
                val resolved = resolve(session, LibraryVideo(operation.bvid, aid = operation.aid))
                require(resolved.aid > 0) { "视频缺少有效稿件编号" }
                val folder = folders(session, resolved.aid).firstOrNull { it.id == operation.folderId }
                    ?: throw IllegalArgumentException("收藏夹已删除或不属于当前账号")
                if ((folder.favState == 1) != operation.desired) {
                    val response = api.favorite(session.cookieHeader, resolved.aid,
                        operation.folderId.toString().takeIf { operation.desired }.orEmpty(),
                        operation.folderId.toString().takeUnless { operation.desired }.orEmpty(), session.csrf)
                    checkCode(response.code, response.message)
                }
            }
            "HISTORY" -> {
                val resolved = resolve(session, LibraryVideo(operation.bvid, aid = operation.aid, cid = operation.cid))
                require(resolved.aid > 0) { "视频缺少有效稿件编号" }
                val response = if (operation.desired) {
                    require(resolved.cid > 0) { "视频缺少有效分P编号" }
                    // report 接口传实际秒数；-1 完播标记只用于本地展示，不套用 heartbeat 的不同协议。
                    api.report(session.cookieHeader, resolved.aid, resolved.cid, operation.progressSeconds, session.csrf)
                } else api.deleteHistory(session.cookieHeader, "archive_${resolved.aid}", session.csrf)
                checkCode(response.code, response.message)
            }
            else -> throw IllegalArgumentException("未知同步类型")
        }
    }

    private fun LibraryVideoDto.video(): LibraryVideo? = bvid?.takeIf(String::isNotBlank)?.let {
        LibraryVideo(it, aid, cid, title.orEmpty(), pic.orEmpty().toHttps(), owner?.name.orEmpty(), duration)
    }
    private fun <T> data(response: LibraryResponse<T>): T {
        checkCode(response.code, response.message)
        return response.data ?: throw InvalidResponseException("业务成功但缺少 data")
    }
    private fun checkCode(code: Int, message: String?) {
        if (code == -101 || code == -111) throw LoginRequiredException()
        if (code != 0) throw ApiException(code, message)
    }
}
