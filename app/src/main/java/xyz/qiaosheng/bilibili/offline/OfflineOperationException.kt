package xyz.qiaosheng.bilibili.offline

import xyz.qiaosheng.bilibili.core.error.UserFacingException

/** 仅包含本地编写、可直接展示的操作提示，远端异常信息不会放入此类型。 */
class OfflineOperationException(message: String) : UserFacingException(message)

internal inline fun offlineRequire(value: Boolean, message: () -> String) {
    if (!value) throw OfflineOperationException(message())
}

internal inline fun <T : Any> offlineRequireNotNull(value: T?, message: () -> String): T =
    value ?: throw OfflineOperationException(message())
