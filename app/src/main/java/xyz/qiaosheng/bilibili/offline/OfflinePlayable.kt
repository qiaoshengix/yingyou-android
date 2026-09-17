package xyz.qiaosheng.bilibili.offline

import androidx.media3.common.MediaItem
import xyz.qiaosheng.bilibili.model.video.VideoPageData

/** 下载子系统交给播放器的适配结果；Media3 类型留在此处，业务 model 保持纯 Kotlin。 */
data class OfflinePlayable(
    val page: VideoPageData,
    val mediaItem: MediaItem,
    val qualityId: Int,
    val qualityLabel: String
)
