package xyz.qiaosheng.bilibili.model.library

enum class LibraryKind { HISTORY, FAVORITES, LIKES }

/** 时间单位：duration/progress 为秒，viewedAt 为 Unix 秒；账号不嵌入视频元数据。 */
data class LibraryVideo(
    val bvid: String,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val coverUrl: String = "",
    val ownerName: String = "",
    val durationSeconds: Long = 0,
    val progressSeconds: Long = 0,
    val viewedAt: Long = 0,
    val folderId: Long? = null
)

data class FavoriteFolder(val id: Long, val title: String, val mediaCount: Int = 0)

data class LibrarySnapshot(
    val items: List<LibraryVideo> = emptyList(),
    val accountId: Long? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val pendingCount: Int = 0,
    val folders: List<FavoriteFolder> = emptyList(),
    val selectedFolderId: Long? = null,
    val remoteScopeNotice: String? = null,
    val loginRequired: Boolean = false
)

data class VideoLibraryStatus(
    val accountId: Long? = null,
    val liked: Boolean = false,
    val favoriteFolderIds: Set<Long> = emptySet(),
    val pendingCount: Int = 0,
    val error: String? = null
)
