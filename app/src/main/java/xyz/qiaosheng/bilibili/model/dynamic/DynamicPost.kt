package xyz.qiaosheng.bilibili.model.dynamic

/** 页面使用的动态内容，统一承载视频、图片、文章和转发。 */
data class DynamicPost(
    val id: String,
    val authorName: String,
    val authorAvatar: String,
    val publishTimestamp: Long,
    val publishAction: String,
    val text: String,
    val media: DynamicMedia?,
    val forwardedPost: DynamicForwardedPost?,
    val commentCount: Long,
    val forwardCount: Long,
    val likeCount: Long,
    val isLiked: Boolean
)

sealed interface DynamicMedia {
    data class Video(
        val bvid: String,
        val title: String,
        val coverUrl: String,
        val description: String,
        val durationText: String,
        val playCountText: String,
        val danmakuCountText: String
    ) : DynamicMedia

    data class Images(val urls: List<String>) : DynamicMedia

    data class Article(
        val title: String,
        val description: String,
        val coverUrls: List<String>
    ) : DynamicMedia
}

data class DynamicForwardedPost(
    val authorName: String,
    val text: String,
    val media: DynamicMedia?
)
