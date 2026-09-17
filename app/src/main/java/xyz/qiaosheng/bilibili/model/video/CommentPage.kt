package xyz.qiaosheng.bilibili.model.video

data class CommentPage(
    val comments: List<Comment>,
    val nextPage: Int,
    val hasMore: Boolean,
    val totalCount: Long
)
