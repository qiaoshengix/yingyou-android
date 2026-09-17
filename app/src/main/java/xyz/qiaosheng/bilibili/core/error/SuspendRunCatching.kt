package xyz.qiaosheng.bilibili.core.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 在请求边界使用；取消继续传播，JVM Error 不转换成可恢复失败。 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try {
    currentCoroutineContext().ensureActive()
    val value = block()
    currentCoroutineContext().ensureActive()
    Result.success(value)
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    // 某些底层库在取消后抛 IOException，也应优先遵守协程取消。
    currentCoroutineContext().ensureActive()
    Result.failure(exception)
}
