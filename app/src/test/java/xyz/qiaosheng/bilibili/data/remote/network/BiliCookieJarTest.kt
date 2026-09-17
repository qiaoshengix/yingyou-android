package xyz.qiaosheng.bilibili.data.remote.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliCookieJarTest {
    @Test
    fun setAuthCookies_makesLoginCookiesAvailableToApiSubdomain() {
        val cookieJar = BiliCookieJar()
        val passportUrl = "https://passport.bilibili.com/".toHttpUrl()

        cookieJar.saveFromResponse(
            passportUrl,
            listOf(
                hostOnlyCookie("SESSDATA", "session"),
                hostOnlyCookie("DedeUserID", "163519252")
            )
        )

        assertFalse(cookieJar.isLoggedIn())

        cookieJar.setAuthCookies(
            sessdata = "session",
            biliJct = "csrf",
            dedeUserId = "163519252"
        )

        assertTrue(cookieJar.isLoggedIn())
        val apiCookieNames = cookieJar
            .loadForRequest("https://api.bilibili.com/".toHttpUrl())
            .map { cookie -> cookie.name }
            .toSet()
        assertTrue(apiCookieNames.containsAll(setOf("SESSDATA", "bili_jct", "DedeUserID")))
    }

    private fun hostOnlyCookie(name: String, value: String): Cookie = Cookie.Builder()
        .hostOnlyDomain("passport.bilibili.com")
        .name(name)
        .value(value)
        .build()
}
