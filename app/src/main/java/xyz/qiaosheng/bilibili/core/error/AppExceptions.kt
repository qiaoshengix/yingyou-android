package xyz.qiaosheng.bilibili.core.error

/** HTTP 成功不代表业务成功；保留原始 code/message 供诊断使用。 */
class ApiException(val code: Int, message: String?) : Exception(message)

class InvalidResponseException(message: String) : Exception(message)

class LoginRequiredException : Exception("请登录后再操作")

fun requireApiSuccess(code: Int, message: String?) {
    if (code != 0) throw ApiException(code, message)
}

/** 仅用于成功时必须返回 data 的接口，空列表仍是合法数据。 */
fun <T : Any> requireApiData(code: Int, message: String?, data: T?): T {
    requireApiSuccess(code, message)
    return data ?: throw InvalidResponseException("业务成功但缺少 data")
}
