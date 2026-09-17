package xyz.qiaosheng.bilibili.offline

import java.security.MessageDigest
import xyz.qiaosheng.bilibili.model.auth.AuthState

internal fun AuthState.offlineAccountId(): Long? = when (this) {
    AuthState.Initializing -> null
    AuthState.LoggedOut -> 0L
    is AuthState.LoggedIn -> userId
}

internal object OfflineKeys {
    fun id(accountId: Long, bvid: String, cid: Long): String = "offline:$accountId:$bvid:$cid"
    fun cacheKey(id: String, kind: String, streamId: Int, codec: String, bandwidth: Int): String {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("$streamId|$codec|$bandwidth".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return "$id:$kind:$fingerprint"
    }
    fun accountId(key: String?): Long? = key?.split(':')?.takeIf { it.size >= 4 && it[0] == "offline" }
        ?.get(1)?.toLongOrNull()
}
