package xyz.qiaosheng.bilibili.data.remote.dto

import com.google.gson.annotations.SerializedName

/** cursor 接口返回 data.list；稿件标识在 history 内，不能按旧 archives/owner/stat 结构解析。 */
data class HistoryResponse(val code: Int = -1, val data: HistoryData? = null, val message: String? = null)
data class HistoryData(val cursor: HistoryCursor? = null, val list: List<HistoryArchive>? = null)
data class HistoryCursor(
    val max: Long = 0,
    @SerializedName("view_at") val viewAt: Long = 0,
    val business: String = ""
)
data class HistoryArchive(
    val title: String? = null, val cover: String? = null,
    val duration: Long = 0, val progress: Long = 0,
    @SerializedName("view_at") val viewAt: Long = 0,
    @SerializedName("author_name") val authorName: String? = null,
    val history: HistoryTarget? = null
)
data class HistoryTarget(val oid: Long = 0, val bvid: String? = null, val cid: Long = 0, val business: String? = null)
