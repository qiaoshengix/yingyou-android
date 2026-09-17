package xyz.qiaosheng.bilibili.data.paging

import androidx.paging.PagingSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PagingErrorTest {
    private fun loads(error: Exception): List<suspend () -> PagingSource.LoadResult<*, *>> {
        val recommend = RecommendPagingSource { _, _, _ -> throw error }
        val dynamic = DynamicPagingSource { throw error }
        val comment = CommentPagingSource(1) { _, _ -> throw error }
        return listOf(
            { recommend.load(PagingSource.LoadParams.Refresh(null, 12, false)) },
            { dynamic.load(PagingSource.LoadParams.Refresh(null, 20, false)) },
            { comment.load(PagingSource.LoadParams.Refresh(null, 20, false)) }
        )
    }

    @Test fun allPagingSourcesReturnOriginalRequestFailure() = runTest {
        val failure = IOException("network")
        for (load in loads(failure)) {
            assertSame(failure, (load() as PagingSource.LoadResult.Error).throwable)
        }
    }

    @Test fun allPagingSourcesRethrowCancellation() = runTest {
        val cancellation = CancellationException("cancel")
        for (load in loads(cancellation)) {
            try { load(); fail("Cancellation must escape PagingSource") }
            catch (actual: CancellationException) { assertSame(cancellation, actual) }
        }
    }

    @Test fun emptyRecommendationPageStopsPagination() = runTest {
        val source = RecommendPagingSource { _, _, _ -> emptyList() }
        val page = source.load(PagingSource.LoadParams.Refresh(null, 12, false)) as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }
}
