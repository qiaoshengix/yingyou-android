package xyz.qiaosheng.bilibili.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.data.mapper.toUiModel
import xyz.qiaosheng.bilibili.data.paging.DynamicPagingSource
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.dto.DynamicApiItem
import xyz.qiaosheng.bilibili.model.dynamic.DynamicFeedType
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPage
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPost

@Singleton
class DynamicRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    fun getDynamicPager(feedType: DynamicFeedType): Flow<PagingData<DynamicPost>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            DynamicPagingSource { offset -> loadDynamicPage(feedType, offset) }
        }
    ).flow

    private suspend fun loadDynamicPage(
        feedType: DynamicFeedType,
        offset: String?
    ): DynamicPage {
        val response = apiService.getDynamics(
            type = feedType.apiValue,
            offset = offset
        )
        val data = requireApiData(response.code, response.message, response.data)
        return DynamicPage(
            posts = data.items.mapNotNull(DynamicApiItem::toUiModel),
            nextOffset = data.offset.takeIf(String::isNotBlank),
            hasMore = data.hasMore
        )
    }
}
