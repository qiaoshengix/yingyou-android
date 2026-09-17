package xyz.qiaosheng.bilibili.model.video

data class Comment(
    val id: Long,
    val userName: String,
    val avatarUrl: String,
    val content: String,
    val likeCount: Long,
    val replyCount: Int,        // 子回复数
    val timestamp: Long,
)
