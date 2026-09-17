package xyz.qiaosheng.bilibili.data.remote.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class BiliCookieJar(
) : CookieJar {

    private val lock = Any()
    private val cookieStore = mutableListOf<Cookie>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()

        return synchronized(lock) {
            cookieStore.removeAll {
                it.expiresAt <= now
            }

            cookieStore.filter { cookie ->
                cookie.matches(url)
            }
        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        val now = System.currentTimeMillis()

        synchronized(lock) {
            cookies.forEach { newCookie ->

                cookieStore.removeAll { oldCookie ->
                    oldCookie.name == newCookie.name && oldCookie.domain == newCookie.domain && oldCookie.path == newCookie.path
                }
                if (newCookie.expiresAt > now) {
                    cookieStore.add(newCookie)
                }
            }
        }
    }

    fun getCookieValue(host: String, key: String): String? {
        val url = "https://$host/".toHttpUrl()

        return loadForRequest(url).lastOrNull {
            it.name == key
        }?.value
    }

    /**
     * 将登录凭据统一保存为 bilibili.com 域 Cookie。
     *
     * 扫码登录响应来自 passport.bilibili.com，返回的 Cookie 可能是 host-only；
     * 统一域后，api.bilibili.com 等子域才能在当前进程中立即使用登录凭据。
     */
    fun setAuthCookies(
        sessdata: String,
        biliJct: String,
        dedeUserId: String
    ) {
        val authCookies = listOf(
            buildAuthCookie("SESSDATA", sessdata),
            buildAuthCookie("bili_jct", biliJct),
            buildAuthCookie("DedeUserID", dedeUserId)
        )

        synchronized(lock) {
            cookieStore.removeAll { cookie -> cookie.name in AUTH_COOKIE_NAMES }
            cookieStore.addAll(authCookies)
        }
    }

    fun cookieClear() {
        synchronized(lock) {
            cookieStore.clear()
        }
    }

    fun isLoggedIn(): Boolean {
        val sessdata = getCookieValue("bilibili.com", "SESSDATA")
        val userId = getCookieValue("bilibili.com", "DedeUserID")
        return !sessdata.isNullOrBlank() && !userId.isNullOrBlank()
    }

    private fun buildAuthCookie(name: String, value: String): Cookie = Cookie.Builder()
        .domain("bilibili.com")
        .path("/")
        .name(name)
        .value(value)
        .secure()
        .httpOnly()
        .build()

    private companion object {
        val AUTH_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID")
    }

}
