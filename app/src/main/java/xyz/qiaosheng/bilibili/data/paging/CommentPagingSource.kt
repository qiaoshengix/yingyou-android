package xyz.qiaosheng.bilibili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.model.video.Comment
import xyz.qiaosheng.bilibili.model.video.CommentPage

class CommentPagingSource(
    private val aid: Long,
    private val loadPage: suspend (aid: Long, cursor: Int) -> CommentPage
) : PagingSource<Int, Comment>() {
    override fun getRefreshKey(state: PagingState<Int, Comment>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> {
        val currentCursor = params.key ?: 0

        return suspendRunCatching {
            val result = loadPage(aid, currentCursor)
            LoadResult.Page(
                data = result.comments,
                prevKey = null,
                nextKey = if (result.hasMore) result.nextPage else null
            )
        }.getOrElse { LoadResult.Error(it) }
    }
}
