package xyz.qiaosheng.bilibili.data.remote.dto

/** B 站接口的通用响应包装；业务状态判断和错误转换由数据访问边界完成。 */
data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 0
}
