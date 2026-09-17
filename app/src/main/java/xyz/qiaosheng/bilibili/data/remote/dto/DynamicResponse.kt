package xyz.qiaosheng.bilibili.data.remote.dto

/** 动态接口原始载荷；由 DynamicMappers 转换成页面使用的动态模型。 */
data class DynamicResponse(
    val code: Int,
    val message: String? = null,
    val data: DynamicData? = null
)

data class DynamicData(
    val items: List<DynamicApiItem> = emptyList(),
    val offset: String = "",
    val hasMore: Boolean = false
)

data class DynamicApiItem(
    val idStr: String = "",
    val type: String = "",
    val modules: DynamicModules = DynamicModules(),
    val orig: DynamicApiItem? = null
)

data class DynamicModules(
    val moduleAuthor: DynamicAuthor? = null,
    val moduleDynamic: DynamicContent? = null,
    val moduleStat: DynamicStat? = null
)

data class DynamicAuthor(
    val name: String = "",
    val face: String = "",
    val pubTs: Long = 0,
    val pubAction: String = ""
)

data class DynamicContent(
    val desc: DynamicDescription? = null,
    val major: DynamicMajor? = null
)

data class DynamicDescription(
    val text: String = ""
)

data class DynamicMajor(
    val type: String = "",
    val archive: DynamicArchive? = null,
    val draw: DynamicDraw? = null,
    val opus: DynamicOpus? = null,
    val article: DynamicArticle? = null
)

data class DynamicArchive(
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val durationText: String = "",
    val stat: DynamicArchiveStat? = null
)

data class DynamicArchiveStat(
    val play: String = "",
    val danmaku: String = ""
)

data class DynamicDraw(
    val items: List<DynamicDrawItem> = emptyList()
)

data class DynamicDrawItem(
    val src: String = ""
)

data class DynamicOpus(
    val title: String = "",
    val summary: DynamicDescription? = null,
    val pics: List<DynamicOpusPicture> = emptyList()
)

data class DynamicOpusPicture(
    val url: String = ""
)

data class DynamicArticle(
    val title: String = "",
    val covers: List<String> = emptyList(),
    val desc: String = ""
)

data class DynamicStat(
    val comment: DynamicStatItem? = null,
    val forward: DynamicStatItem? = null,
    val like: DynamicLikeStat? = null
)

data class DynamicStatItem(
    val count: Long = 0
)

data class DynamicLikeStat(
    val count: Long = 0,
    val status: Boolean = false
)
