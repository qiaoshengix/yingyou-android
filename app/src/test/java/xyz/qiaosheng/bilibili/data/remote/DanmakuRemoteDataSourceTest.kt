package xyz.qiaosheng.bilibili.data.remote

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.api.DanmakuApiService
import xyz.qiaosheng.bilibili.data.remote.dto.NavData
import xyz.qiaosheng.bilibili.data.remote.dto.NavResponse
import xyz.qiaosheng.bilibili.data.remote.dto.WbiImage
import xyz.qiaosheng.bilibili.data.remote.network.WbiSigner
import xyz.qiaosheng.bilibili.di.DanmakuModule
import xyz.qiaosheng.bilibili.support.fakeApi

class DanmakuRemoteDataSourceTest {
    @Test fun requestsWbiProtobufEndpointWithCapturedCookiesWithoutUsingSharedCookieJar() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            var sharedCookieReads = 0
            var sharedCookieWrites = 0
            val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    sharedCookieReads++
                    return listOf(Cookie.Builder().domain(url.host).name("SESSDATA").value("new-session").build())
                }
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { sharedCookieWrites++ }
            }).addInterceptor { chain ->
                val original = chain.request()
                chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath)
                    .newBuilder().encodedQuery(original.url.encodedQuery).build()).build())
            }.build()
            server.enqueue(MockResponse().setBody("").setHeader("Content-Type", "application/octet-stream")
                .setHeader("Set-Cookie", "SESSDATA=late-response; Path=/"))
            val remote = RetrofitDanmakuRemoteDataSource(DanmakuModule.api(client), signer(), DanmakuParser())
            val session = DanmakuSessionSnapshot(DanmakuSessionKey(1, 1), "SESSDATA=captured-session; DedeUserID=1")
            assertTrue(remote.loadSegment(session, 11, 22, 2).isEmpty())
            val request = server.takeRequest(5, TimeUnit.SECONDS) ?: error("Missing local request")
            val url = requireNotNull(request.requestUrl)
            assertEquals("/x/v2/dm/wbi/web/seg.so", url.encodedPath)
            assertEquals("1", url.queryParameter("type"))
            assertEquals("11", url.queryParameter("pid"))
            assertEquals("22", url.queryParameter("oid"))
            assertEquals("2", url.queryParameter("segment_index"))
            assertTrue(url.queryParameter("w_rid").orEmpty().matches(Regex("[0-9a-f]{32}")))
            assertNotNull(url.queryParameter("wts"))
            assertEquals(session.cookieHeader, request.getHeader("Cookie"))
            assertEquals(0, sharedCookieReads)
            assertEquals(0, sharedCookieWrites)
            assertFalse(session.toString().contains("captured-session"))
        } finally { server.shutdown() }
    }

    @Test fun rejectsOversizedSuccessAndErrorBodiesIncludingChunkedTransfer() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/segment")).build())
            }.build()
            val api = DanmakuModule.api(client)
            val oversized = "x".repeat(DanmakuParser.MAX_PAYLOAD_BYTES + 1)
            for (status in listOf(200, 500)) {
                server.enqueue(MockResponse().setResponseCode(status).setChunkedBody(oversized, 8192))
                val error = runCatching {
                    val body = api.segment("", emptyMap())
                    DanmakuParser().parse(body, 1)
                }.exceptionOrNull()
                assertTrue("status $status must reject oversized data", error is IOException)
            }
        } finally { server.shutdown() }
    }

    @Test fun cancellingRemoteLoadClosesAndUnblocksTheStreamingBody() = runTest {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val didClose = AtomicBoolean(false)
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.countDown()
                check(closed.await(5, TimeUnit.SECONDS)) { "Body was not closed on cancellation" }
                return -1
            }
            override fun timeout() = Timeout.NONE
            override fun close() { didClose.set(true); closed.countDown() }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = source
        }
        val api = fakeApi<DanmakuApiService> { _, _ -> body }
        val remote = RetrofitDanmakuRemoteDataSource(api, signer(), DanmakuParser())
        val request = async { remote.loadSegment(DanmakuSessionSnapshot(DanmakuSessionKey(null, 1), ""), 1, 2, 1) }
        assertTrue(withContext(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        assertTrue(didClose.get())
    }

    private fun signer() = WbiSigner(fakeApi<BiliApiService> { method, _ ->
        check(method == "getNav")
        NavResponse(0, null, NavData(WbiImage(
            "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
            "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png",
        )))
    })
}
