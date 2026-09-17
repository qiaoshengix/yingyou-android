package xyz.qiaosheng.bilibili.ui.mine

import xyz.qiaosheng.bilibili.model.profile.UserProfile

sealed interface MineUiState {
    data object Loading : MineUiState
    data object LoggedOut : MineUiState
    data class Content(val userProfile: UserProfile) : MineUiState
    data class Error(val message: String) : MineUiState
}
