package xyz.qiaosheng.bilibili.data.remote.dto

data class CommentResponse(
    val code: Int,
    val message: String? = null,
    val data: CommentData?
)

data class CommentData(
    val cursor: CommentCursor?,
    val replies: List<ReplyItem>? = emptyList()
)

data class CommentCursor(
    val next: Int = 0,
    val isEnd: Boolean = true,
    val allCount: Long = 0
)

data class ReplyItem(
    val rpid: Long,
    val ctime: Long,
    val like: Long = 0,
    val rcount: Int = 0,
    val member: ReplyMember,
    val content: ReplyContent
)

data class ReplyMember(
    val mid: String = "",
    val uname: String = "",
    val avatar: String = ""
)

data class ReplyContent(
    val message: String = ""
)

data class AddCommentResponse(
    val code: Int,
    val message: String? = null,
    val data: AddCommentData?
)

data class AddCommentData(
    val reply: ReplyItem?
)
