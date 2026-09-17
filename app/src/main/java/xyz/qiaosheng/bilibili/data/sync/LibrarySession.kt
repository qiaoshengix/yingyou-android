package xyz.qiaosheng.bilibili.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.HttpUrl.Companion.toHttpUrl
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.auth.AuthState

/** 不使用 data class，避免凭据被自动生成的 toString 输出到日志。 */
class LibraryCredentials(val accountId: Long, val cookieHeader: String, val csrf: String, val sessionVersion: Long = 0) {
    override fun toString() = "LibraryCredentials(accountId=$accountId, credentials=redacted)"
}

interface LibrarySession {
    val accountId: StateFlow<Long?>
    val changes: Flow<Long?> get() = accountId
    fun isCurrent(accountId: Long): Boolean
    fun isCurrent(credentials: LibraryCredentials): Boolean = isCurrent(credentials.accountId)
    suspend fun initialize()
    suspend fun credentials(accountId: Long): LibraryCredentials
}

@Singleton
class AuthLibrarySession @Inject constructor(
    private val auth: AuthSessionManager,
    private val cookies: BiliCookieJar,
    @LibraryScope scope: CoroutineScope
) : LibrarySession {
    override val accountId = auth.authState.map { (it as? AuthState.LoggedIn)?.userId }
        .stateIn(scope, SharingStarted.Eagerly, (auth.authState.value as? AuthState.LoggedIn)?.userId)
    // 同账号重新扫码也必须触发唤醒，不能只观察 userId 去重后的状态。
    override val changes = combine(auth.authState, auth.sessionVersion) { state, _ -> (state as? AuthState.LoggedIn)?.userId }
    override fun isCurrent(accountId: Long): Boolean =
        ((auth.authState.value as? AuthState.LoggedIn)?.userId ?: 0L) == accountId
    override fun isCurrent(credentials: LibraryCredentials): Boolean =
        isCurrent(credentials.accountId) && credentials.sessionVersion == auth.sessionVersion.value
    override suspend fun initialize() = auth.initialize()

    override suspend fun credentials(accountId: Long): LibraryCredentials = auth.withAccountSession(accountId) {
        val values = cookies.loadForRequest("https://api.bilibili.com/".toHttpUrl()).associate { it.name to it.value }
        if (values["DedeUserID"]?.toLongOrNull() != accountId || values["SESSDATA"].isNullOrBlank()) throw LoginRequiredException()
        LibraryCredentials(accountId, values.entries.joinToString("; ") { "${it.key}=${it.value}" },
            values["bili_jct"].orEmpty(), auth.sessionVersion.value)
    }
}
