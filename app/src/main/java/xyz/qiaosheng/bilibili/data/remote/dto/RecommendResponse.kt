package xyz.qiaosheng.bilibili.data.remote.dto

data class RecommendResponse(
    val code: Int,
    val data: RecommendData?,
    val message: String? = null
)

data class RecommendData(
    val item: List<RecommendItem>
)

data class RecommendItem(
    val id: Long,               // 视频 ID
    val bvid: String,           // BV 号
    val title: String,
    val pic: String,            // 封面 URL
    val duration: Int,          // 时长（秒）
    val pubdate: Long,
    val owner: RecommendOwner,  // UP主信息
    val stat: RecommendStat,    // 统计数据
    val cid: Long               // 视频 cid（弹幕用）
)

data class RecommendOwner(
    val mid: Long,
    val name: String,
    val face: String            // 头像 URL
)

data class RecommendStat(
    val view: Long,             // 播放量
    val danmaku: Long,          // 弹幕数
    val reply: Long,            // 评论数
    val favorite: Long,         // 收藏数
    val coin: Long,             // 投币数
    val like: Long,             // 点赞数
    val share: Long = 0,        // 分享数
)
