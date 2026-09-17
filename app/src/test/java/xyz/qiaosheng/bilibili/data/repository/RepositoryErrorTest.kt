package xyz.qiaosheng.bilibili.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.core.network.*
import xyz.qiaosheng.bilibili.data.mapper.*
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.dto.*
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.auth.*
import xyz.qiaosheng.bilibili.model.dynamic.*
import xyz.qiaosheng.bilibili.model.history.*
import xyz.qiaosheng.bilibili.model.profile.*
import xyz.qiaosheng.bilibili.model.search.*
import xyz.qiaosheng.bilibili.model.video.*
import xyz.qiaosheng.bilibili.support.fakeApi

class RepositoryErrorTest {
    @Test fun recommendationBusinessFailureDoesNotBecomeEmptyPage() = runTest {
        val api = fakeApi<BiliApiService> { _, _ -> RecommendResponse(code = -1, data = null) }
        val result = suspendRunCatching { VideoRecommendRepository(api).getRecommendVideoList(12, 1, 1) }
        assertTrue(result.exceptionOrNull() is ApiException)
    }

    @Test fun recommendationMissingDataDoesNotBecomeEmptyPage() = runTest {
        val api = fakeApi<BiliApiService> { _, _ -> RecommendResponse(code = 0, data = null) }
        val result = suspendRunCatching { VideoRecommendRepository(api).getRecommendVideoList(12, 1, 1) }
        assertTrue(result.exceptionOrNull() is InvalidResponseException)
    }

    @Test fun commentRequiresLoginBeforeSending() = runTest {
        var requested = false
        val api = fakeApi<BiliApiService> { _, _ -> requested = true; error("Unexpected call") }
        val result = suspendRunCatching { VideoRepository(api, BiliCookieJar()).addComment(1, "text") }
        assertTrue(result.exceptionOrNull() is LoginRequiredException)
        assertFalse(requested)
    }

    @Test fun successfulCommentWithoutEchoIsStillSuccessful() = runTest {
        val api = fakeApi<BiliApiService> { method, _ ->
            assertEquals("addVideoComment", method)
            AddCommentResponse(code = 0, data = null)
        }
        val cookies = BiliCookieJar().apply { setAuthCookies("test", "csrf", "1") }
        assertNull(VideoRepository(api, cookies).addComment(1, "text"))
    }

    @Test fun failedCommentPreservesBusinessErrorCode() = runTest {
        val api = fakeApi<BiliApiService> { _, _ -> AddCommentResponse(code = -400, data = null) }
        val cookies = BiliCookieJar().apply { setAuthCookies("test", "csrf", "1") }
        val result = suspendRunCatching { VideoRepository(api, cookies).addComment(1, "text") }
        assertEquals(-400, (result.exceptionOrNull() as ApiException).code)
    }
}
