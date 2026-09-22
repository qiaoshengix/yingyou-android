package xyz.qiaosheng.bilibili.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.mapper.toUiModel
import xyz.qiaosheng.bilibili.data.paging.RecommendPagingSource
import xyz.qiaosheng.bilibili.data.paging.RecommendRemoteMediator
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

@Singleton
class VideoRecommendRepository @Inject constructor(
    val apiService: BiliApiService,
    private val database: BiliDatabase
) {
    suspend fun getRecommendVideoList(
        ps: Int,
        freshIdx: Int,
        freshIdx1h: Int
    ): List<RecommendVideo> {
        val response = apiService.getRecommendVideos(
            ps = ps,
            freshIdx = freshIdx,
            freshIdx1h = freshIdx1h
        )
        return requireApiData(response.code, response.message, response.data)
            .item
            .filter { it.bvid.isNotBlank() }
            .distinctBy { it.bvid }
            .map { it.toUiModel() }
    }

    @OptIn(ExperimentalPagingApi::class)
    fun getRecommendVideoPager(
        accountId: Long
    ): Flow<PagingData<RecommendVideo>> {
        return Pager(
            config = PagingConfig(
                pageSize = 12,
                initialLoadSize = 12,
                enablePlaceholders = false
            ),
            remoteMediator = RecommendRemoteMediator(
                accountId = accountId,
                database = database,
                loadPage = ::getRecommendVideoList
            ),
            pagingSourceFactory = {
                database.recommendDao().pagingSource(accountId)
            }
        ).flow.map {
            pagingData ->
            pagingData.map { entity -> entity.toUiModel() }
        }
    }

    fun getRecommendVideoPager(): Flow<PagingData<RecommendVideo>> {
        return Pager(
            config = PagingConfig(
                pageSize = 12,
                enablePlaceholders = false,
                initialLoadSize = 12
            ),
            pagingSourceFactory = {
                RecommendPagingSource(
                    loadPage = ::getRecommendVideoList
                )
            }
        ).flow
    }
}
