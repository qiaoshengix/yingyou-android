package xyz.qiaosheng.bilibili.model.offline

enum class OfflineStatus { QUEUED, DOWNLOADING, PAUSED, WAITING_FOR_NETWORK, COMPLETED, FAILED, REMOVING }

data class OfflineVideo(
    val id: String,
    val accountId: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val ownerName: String,
    val qualityId: Int,
    val qualityLabel: String,
    val status: OfflineStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    val createdAt: Long
) {
    val progress: Float? get() = totalBytes.takeIf { it > 0 }?.let {
        (downloadedBytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat()
    }
}
