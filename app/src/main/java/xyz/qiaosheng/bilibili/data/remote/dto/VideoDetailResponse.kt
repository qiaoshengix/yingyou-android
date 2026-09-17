package xyz.qiaosheng.bilibili.data.remote.dto

data class VideoDetailResponse(
    val code: Int,
    val message: String? = null,
    val data: VideoDetailData?
)

data class VideoDetailData(
    val bvid: String,
    val aid: Long,
    val cid: Long,              // 弹幕和播放都需要
    val title: String,
    val desc: String,
    val pic: String,
    val duration: Int,
    val pubdate: Long,
    val owner: RecommendOwner,
    val stat: RecommendStat,
    val tags: List<Tag>?,
    val pages: List<VideoPart>? = null
)

/** 每个分 P 有独立 cid 和时长；历史恢复必须播放对应分 P。 */
data class VideoPart(val cid: Long, val page: Int, val part: String, val duration: Int)

data class Tag(
    val tag_name: String,
    val tag_id: Long
)
