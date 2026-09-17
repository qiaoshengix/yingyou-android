package xyz.qiaosheng.bilibili.core.error

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 每个处理边界记录一次；构造参数允许测试替换 Android 日志。 */
@Singleton
class ErrorReporter(private val logger: (String, Throwable) -> Unit) {
    @Inject constructor() : this({ operation, error ->
        Log.e("Bilibili", operation, error)
    })

    fun message(operation: String, error: Throwable): String {
        if (error is CancellationException) throw error
        logger(operation, error)
        return "$operation：${error.toUserMessage()}"
    }
}
