package xyz.qiaosheng.bilibili.model.history

data class HistoryItem(
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val duration: Int,
    val progress: Int,            // 已看时长
    val viewTime: Long,           // 观看时间
    val ownerName: String,
    val playCount: Long
) {
    // 进度百分比
    val progressPercent: Float
        get() = if (duration > 0) progress.toFloat() / duration else 0f
}
