package xyz.qiaosheng.bilibili.ui.navigation

object Routes {
    // 三个底部 Tab 共用的根导航目的地
    const val MAIN = "main"

    // 底部 Tab 标识。它们只表示选中状态，不进入 Navigation 返回栈。
    const val HOME = "home"
    const val DYNAMIC = "dynamic"
    const val MINE = "mine"

    // 页面内导航
    const val VIDEO_DETAIL = "video/{bvid}?cid={cid}&offline={offline}"
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val LIKES = "likes"
    const val OFFLINE = "offline"

    const val SERVICE_LAB = "service_lab"

    fun videoDetail(bvid: String, cid: Long = 0L, offline: Boolean = false) =
        "video/$bvid?cid=$cid&offline=$offline"
}
