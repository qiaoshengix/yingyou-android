package xyz.qiaosheng.bilibili.data.mapper

import xyz.qiaosheng.bilibili.core.network.toHttps
import xyz.qiaosheng.bilibili.data.remote.dto.SearchItem
import xyz.qiaosheng.bilibili.model.search.SearchResult

/** 丢弃没有 BV 号或标题的结果，并移除接口返回的搜索高亮标签。 */
fun SearchItem.toUiModel(): SearchResult? {
    if (bvid.isNullOrBlank() || title == null) return null
    return SearchResult(
        bvid = bvid,
        title = title.replace(Regex("<[^>]+>"), ""),  // 去除 <em class="keyword"> 高亮标签
        coverUrl = pic?.toHttps() ?: "",
        duration = duration ?: "00:00",
        playCount = play ?: 0,
        author = author ?: "未知",
        authorAvatar = upic ?: "",
        description = description ?: ""
    )
}
