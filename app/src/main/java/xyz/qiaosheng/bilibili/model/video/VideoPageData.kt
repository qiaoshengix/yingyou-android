package xyz.qiaosheng.bilibili.model.video

/** 详情页的加载结果；aid 用于评论，cid 用于播放和弹幕。 */
data class VideoPageData(
    val aid: Long,
    val cid: Long,
    val detail: VideoDetail
)
