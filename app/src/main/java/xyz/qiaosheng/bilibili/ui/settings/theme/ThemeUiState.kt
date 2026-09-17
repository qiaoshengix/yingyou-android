package xyz.qiaosheng.bilibili.ui.settings.theme

import xyz.qiaosheng.bilibili.model.settings.ThemePreferences

/** 已保存偏好与读写状态；保存失败时不把未落盘的选择当作当前主题。 */
data class ThemeUiState(
    val preferences: ThemePreferences = ThemePreferences(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null
) {
    val canEdit: Boolean get() = !isLoading && !isSaving && loadError == null
}
