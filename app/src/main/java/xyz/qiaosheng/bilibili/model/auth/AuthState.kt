package xyz.qiaosheng.bilibili.model.auth

/** 会话初始化和登录状态；认证协调器是该状态的唯一写入方。 */
sealed interface AuthState {
    data object Initializing : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val userId: Long) : AuthState
}
