package xyz.qiaosheng.bilibili.ui.video

import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

data class DanmakuUiState(
    val enabled: Boolean = true,
    val preferencesReady: Boolean = false,
    val items: List<DanmakuItem> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val unavailableReason: String? = null,
)
