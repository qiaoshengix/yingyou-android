package xyz.qiaosheng.bilibili.data.remote

import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.data.sync.LibraryCredentials
import xyz.qiaosheng.bilibili.di.LibraryModule

class LibraryCookieIsolationTest {
    @Test fun realOkHttpBridgeCannotReplaceCapturedAccountCookieAfterAccountSwitch() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val captured = LibraryCredentials(1, "DedeUserID=1; SESSDATA=account-one", "csrf-one")
            var sharedCookieReads = 0
            var sharedCookieWrites = 0
            val sharedJar = object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    sharedCookieReads++
                    return listOf(Cookie.Builder().domain(url.host).name("SESSDATA").value("account-two").build())
                }
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { sharedCookieWrites++ }
            }
            val baseClient = OkHttpClient.Builder().cookieJar(sharedJar).addInterceptor { chain ->
                // 只重定向到测试服务器，其后仍真实经过 OkHttp 的 Cookie 桥接与网络拦截链。
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }.build()
            server.enqueue(MockResponse().setBody("""{"code":0}""").addHeader("Set-Cookie", "SESSDATA=response-cookie; Path=/"))
            val api = LibraryModule.api(baseClient, Gson())
            api.like(captured.cookieHeader, "BV-test", 1, captured.csrf)
            val request = server.takeRequest(5, TimeUnit.SECONDS) ?: error("No local request")
            assertEquals("DedeUserID=1; SESSDATA=account-one", request.getHeader("Cookie"))
            assertTrue(request.body.readUtf8().contains("csrf=csrf-one"))
            assertEquals(0, sharedCookieReads); assertEquals(0, sharedCookieWrites)
            assertFalse(captured.toString().contains("account-one"))
        } finally { server.shutdown() }
    }
}
