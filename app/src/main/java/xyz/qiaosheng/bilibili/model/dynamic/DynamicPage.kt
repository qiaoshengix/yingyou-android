package xyz.qiaosheng.bilibili.model.dynamic

/** 分页所需的业务结果；offset 由服务端生成，不能自行递增。 */
data class DynamicPage(
    val posts: List<DynamicPost>,
    val nextOffset: String?,
    val hasMore: Boolean
)
