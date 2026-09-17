package xyz.qiaosheng.bilibili.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.qiaosheng.bilibili.data.local.preferences.AuthDataStore
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException

/** 串行同步持久化 Cookie、网络 Cookie 和登录状态，避免恢复会话与退出登录互相覆盖。 */
@Singleton
class AuthSessionManager @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val cookieJar: BiliCookieJar
) {
    private val sessionMutex = Mutex()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState = _authState.asStateFlow()
    private val _sessionVersion = MutableStateFlow(0L)
    val sessionVersion = _sessionVersion.asStateFlow()

    /** 在账号切换锁内捕获凭据；调用方把快照交给禁用共享 CookieJar 的专用客户端。 */
    suspend fun <T> withAccountSession(expectedId: Long, block: suspend () -> T): T = sessionMutex.withLock {
        if ((_authState.value as? AuthState.LoggedIn)?.userId != expectedId) throw LoginRequiredException()
        block()
    }

    suspend fun initialize(): Unit = sessionMutex.withLock {
        if (_authState.value != AuthState.Initializing) return@withLock

        val (sessdata, biliJct, dedeUserId) = authDataStore.getCookies()
        val userId = dedeUserId?.toLongOrNull()

        if (!sessdata.isNullOrBlank() && userId != null) {
            cookieJar.setAuthCookies(
                sessdata = sessdata,
                biliJct = biliJct.orEmpty(),
                dedeUserId = dedeUserId
            )
            _authState.value = AuthState.LoggedIn(userId)
        } else {
            cookieJar.cookieClear()
            authDataStore.clearCookies()
            _authState.value = AuthState.LoggedOut
        }
        _sessionVersion.value++
    }

    suspend fun establishSession(
        sessdata: String,
        biliJct: String,
        dedeUserId: String
    ): Unit = sessionMutex.withLock {
        val userId = requireNotNull(dedeUserId.toLongOrNull()) {
            "登录响应缺少有效的 DedeUserID"
        }

        authDataStore.saveCookie(sessdata, biliJct, dedeUserId)
        cookieJar.setAuthCookies(sessdata, biliJct, dedeUserId)
        _authState.value = AuthState.LoggedIn(userId)
        _sessionVersion.value++
    }

    suspend fun logout(): Unit = sessionMutex.withLock {
        // 持久化清理成功后再发布退出状态；失败时允许用户重试。
        authDataStore.clearCookies()
        cookieJar.cookieClear()
        _authState.value = AuthState.LoggedOut
        _sessionVersion.value++
    }
}
