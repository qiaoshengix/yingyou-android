package xyz.qiaosheng.bilibili.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.core.error.requireApiSuccess
import xyz.qiaosheng.bilibili.data.mapper.toUiModel
import xyz.qiaosheng.bilibili.data.paging.CommentPagingSource
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.dto.PlayData
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.video.Comment
import xyz.qiaosheng.bilibili.model.video.CommentPage
import xyz.qiaosheng.bilibili.model.video.VideoPageData

@Singleton
class VideoRepository @Inject constructor(
    private val apiService: BiliApiService,
    private val cookieJar: BiliCookieJar
) {
    suspend fun getVideoPage(bvid: String, requestedCid: Long? = null): VideoPageData {
        val response = apiService.getVideoDetail(bvid)
        val rawDetail = requireApiData(response.code, response.message, response.data)
        val targetCid = requestedCid?.takeIf { it > 0L } ?: rawDetail.cid
        val part = rawDetail.pages.orEmpty().firstOrNull { it.cid == targetCid }
        if (targetCid != rawDetail.cid && part == null) {
            throw InvalidResponseException("这条观看记录对应的分 P 已不可用")
        }

        val followerCount = try {
            val relation = apiService.getRelationStat(rawDetail.owner.mid)
            requireApiData(relation.code, relation.message, relation.data).follower
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // UP 主粉丝数是补充数据，不应阻塞详情页主体。
            0
        }

        val detail = rawDetail.toUiModel().let { video ->
            video.copy(
                owner = video.owner.copy(fansCount = followerCount),
                duration = part?.duration ?: video.duration
            )
        }
        return VideoPageData(
            aid = rawDetail.aid,
            cid = targetCid,
            detail = detail
        )
    }

    suspend fun getVideoPlayUrl(bvid: String, cid: Long): PlayData {
        val response = apiService.getPlayUrl(bvid = bvid, cid = cid)
        return requireApiData(response.code, response.message, response.data)
    }

    suspend fun getComments(aid: Long, page: Int): CommentPage {
        val response = apiService.getVideoComments(aid = aid, next = page)
        val data = requireApiData(response.code, response.message, response.data)
        val cursor = data.cursor
        return CommentPage(
            comments = data.replies.orEmpty().map { it.toUiModel() },
            nextPage = cursor?.next ?: page,
            hasMore = cursor?.isEnd == false,
            totalCount = cursor?.allCount ?: 0
        )
    }

    fun getCommentPager(aid: Long): Flow<PagingData<Comment>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                CommentPagingSource(
                    aid = aid,
                    loadPage = ::getComments
                )
            }
        ).flow
    }

    suspend fun addComment(aid: Long, message: String): Comment? {
        val csrf = cookieJar.getCookieValue("api.bilibili.com", "bili_jct")
            ?.takeIf(String::isNotBlank) ?: throw LoginRequiredException()
        val response = apiService.addVideoComment(
            aid = aid,
            message = message,
            csrf = csrf,
            csrfToken = csrf
        )
        // 写操作只以业务状态确认成功；响应未附带评论详情不应诱导重复发送。
        requireApiSuccess(response.code, response.message)
        return response.data?.reply?.toUiModel()
    }
}
