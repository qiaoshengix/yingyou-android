package xyz.qiaosheng.bilibili.model.video

data class VideoDetail(
    val bvid: String,
    val title: String,
    val desc: String,           // 简介
    val coverUrl: String,
    val duration: Int,
    val playCount: Long,
    val danmakuCount: Long,
    val likeCount: Long,
    val coinCount: Long,
    val favoriteCount: Long,
    val shareCount: Long,
    val replyCount: Long,       // 评论数
    val pubdate: Long,          // 发布时间戳
    val owner: VideoOwner,
)

data class VideoOwner(
    val mid: Long,              // 用户 ID
    val name: String,
    val avatarUrl: String,
    val fansCount: Long,
    val following: Boolean,     // 是否已关注
)
