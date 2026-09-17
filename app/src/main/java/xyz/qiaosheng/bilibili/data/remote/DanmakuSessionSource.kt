package xyz.qiaosheng.bilibili.data.remote

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.auth.AuthState

data class DanmakuSessionKey(val accountId: Long?, val version: Long)

/** Credentials deliberately have no generated toString/copy/component functions. */
class DanmakuSessionSnapshot(val key: DanmakuSessionKey, val cookieHeader: String) {
    override fun toString() = "DanmakuSessionSnapshot(credentials=redacted)"
}

interface DanmakuSessionSource {
    suspend fun snapshot(): DanmakuSessionSnapshot
    fun isCurrent(key: DanmakuSessionKey): Boolean
}

class AuthDanmakuSessionSource @Inject constructor(
    private val auth: AuthSessionManager,
    private val cookies: BiliCookieJar,
) : DanmakuSessionSource {
    override suspend fun snapshot(): DanmakuSessionSnapshot {
        auth.initialize()
        val accountId = (auth.authState.value as? AuthState.LoggedIn)?.userId
        val snapshot = if (accountId != null) {
            auth.withAccountSession(accountId) { capture(accountId) }
        } else {
            capture(null)
        }
        if (!isCurrent(snapshot.key)) throw CancellationException("Danmaku account changed")
        return snapshot
    }

    private fun capture(accountId: Long?): DanmakuSessionSnapshot {
        val key = DanmakuSessionKey(accountId, auth.sessionVersion.value)
        val values = cookies.loadForRequest("https://api.bilibili.com/".toHttpUrl())
            // A guest snapshot must never accidentally include a concurrent login's credentials.
            .filter { accountId != null || it.name !in AUTH_COOKIE_NAMES }
        return DanmakuSessionSnapshot(key, values.joinToString("; ") { "${it.name}=${it.value}" })
    }

    override fun isCurrent(key: DanmakuSessionKey): Boolean =
        auth.authState.value != AuthState.Initializing &&
            (auth.authState.value as? AuthState.LoggedIn)?.userId == key.accountId &&
            auth.sessionVersion.value == key.version

    private companion object {
        val AUTH_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID")
    }
}
