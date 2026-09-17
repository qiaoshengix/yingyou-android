package xyz.qiaosheng.bilibili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPage
import xyz.qiaosheng.bilibili.model.dynamic.DynamicPost

class DynamicPagingSource(
    private val loadPage: suspend (offset: String?) -> DynamicPage
) : PagingSource<String, DynamicPost>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, DynamicPost> {
        return suspendRunCatching {
            val page = loadPage(params.key)
            LoadResult.Page(
                data = page.posts,
                prevKey = null,
                nextKey = page.nextOffset.takeIf { page.hasMore }
            )
        }.getOrElse { LoadResult.Error(it) }
    }

    override fun getRefreshKey(state: PagingState<String, DynamicPost>): String? = null
}
