package xyz.qiaosheng.bilibili.model.search

/** 清理接口高亮标签后，供搜索结果页面使用的视频条目。 */
data class SearchResult(
    val bvid: String,
    val title: String,        // 去除高亮标签的纯文本
    val coverUrl: String,
    val duration: String,
    val playCount: Long,
    val author: String,
    val authorAvatar: String,
    val description: String
)
