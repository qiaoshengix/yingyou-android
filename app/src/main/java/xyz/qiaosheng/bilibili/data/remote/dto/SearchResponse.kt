package xyz.qiaosheng.bilibili.data.remote.dto

data class SearchResponse(
    val code: Int,
    val data: SearchData?,
    val message: String? = null
)

data class SearchData(
    val result: List<SearchItem>?,   // 搜索结果
    val page: Int,                   // 当前页
    val pagesize: Int                // 每页数量
)

data class SearchItem(
    val type: String,         // "video" / "user" / "live"
    val bvid: String?,
    val title: String?,       // 标题（带高亮标签）
    val pic: String?,         // 封面
    val duration: String?,    // 时长 "10:34"
    val play: Long?,          // 播放量
    val danmaku: Long?,       // 弹幕数
    val mid: Long?,           // UP主 ID
    val author: String?,      // UP主名
    val upic: String?,        // UP主头像
    val description: String?  // 简介
)
