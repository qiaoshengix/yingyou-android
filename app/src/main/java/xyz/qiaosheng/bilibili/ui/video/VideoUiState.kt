package xyz.qiaosheng.bilibili.ui.video

import xyz.qiaosheng.bilibili.model.video.VideoDetail
import xyz.qiaosheng.bilibili.ui.video.player.VideoQuality

/** 视频详情加载成功后的页面状态；播放器实例由 ViewModel 单独持有。 */
data class VideoUiState(
    val video: VideoDetail,
    val qualities: List<VideoQuality>,
    val selectedQualityId: Int,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val isOffline: Boolean = false,
    val isAudioOnly: Boolean = false,
    val totalCommentCount: Long = video.replyCount,
    val commentDraft: String = "",
    val commentSubmitting: Boolean = false,
    val commentMessage: String? = null,
    // 评论提交成功后递增，由页面触发 Paging 刷新。
    val commentsRefreshVersion: Int = 0,
    val playbackErrorMessage: String? = null
)
