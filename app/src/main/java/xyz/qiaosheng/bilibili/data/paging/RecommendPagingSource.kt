package xyz.qiaosheng.bilibili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.model.video.RecommendVideo

class RecommendPagingSource(
    val loadPage: suspend (
        ps: Int,
        freshIdx: Int,
        freshIdx1h: Int
    ) -> List<RecommendVideo>
) : PagingSource<Int, RecommendVideo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendVideo> {
        val currentCursor = params.key ?: 1
        return suspendRunCatching {
            val result = loadPage(
                12,
                currentCursor,
                currentCursor
            )

            LoadResult.Page(
                data = result,
                prevKey = null,
                nextKey = if (result.isEmpty()) null else currentCursor + 1
            )
        }.getOrElse { LoadResult.Error(it) }
    }

    override fun getRefreshKey(state: PagingState<Int, RecommendVideo>): Int? = null
}
