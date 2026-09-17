package xyz.qiaosheng.bilibili.core.error

import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** 纯映射，不记录日志、不弹 Toast、不导航，也不清理登录态。 */
fun Throwable.toUserMessage(): String = when (this) {
    is CancellationException -> throw this
    is UserFacingException -> userMessage
    is LoginRequiredException -> "请登录后再操作"
    is ApiException -> "服务未能完成请求（错误码：$code）"
    is InvalidResponseException -> "服务返回的数据不完整，请稍后重试"
    is SocketTimeoutException -> "请求超时，请重试"
    is UnknownHostException -> "无法连接服务器，请检查网络"
    is SSLException -> "安全连接失败，请稍后重试"
    is HttpException -> when (code()) {
        401 -> "登录验证失败，请重新登录"
        403 -> "服务器拒绝了请求"
        404 -> "请求的资源不存在"
        429 -> "请求过于频繁，请稍后重试"
        in 500..599 -> "服务器暂时不可用，请稍后重试"
        else -> "网络请求失败（HTTP ${code()}）"
    }
    is JsonParseException -> "数据解析失败，请稍后重试"
    is IOException -> "连接或读写失败，请重试"
    else -> "操作失败，请稍后重试"
}
