package xyz.qiaosheng.bilibili.ui.library

import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibrarySnapshot

/** 仓库持有列表与同步状态；页面仅补充加载入口、操作反馈和按钮忙碌状态。 */
data class LibraryUiState(
    val kind: LibraryKind = LibraryKind.HISTORY,
    val snapshot: LibrarySnapshot? = null,
    val initialLoading: Boolean = true,
    val operationInProgress: Boolean = false,
    val requestError: String? = null,
    val actionMessage: String? = null
)

internal val LibraryKind.title: String
    get() = when (this) {
        LibraryKind.HISTORY -> "历史记录"
        LibraryKind.FAVORITES -> "我的收藏"
        LibraryKind.LIKES -> "我的点赞"
    }
