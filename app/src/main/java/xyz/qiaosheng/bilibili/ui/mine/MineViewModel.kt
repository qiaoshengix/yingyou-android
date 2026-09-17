package xyz.qiaosheng.bilibili.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.repository.MineRepository
import xyz.qiaosheng.bilibili.model.auth.AuthState

@HiltViewModel
class MineViewModel @Inject constructor(
    private val mineRepository: MineRepository,
    private val authSessionManager: AuthSessionManager,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _uiState = MutableStateFlow<MineUiState>(MineUiState.Loading)

    val uiState = _uiState.asStateFlow()
    private var profileJob: Job? = null
    private var logoutJob: Job? = null
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()

    fun dismissActionMessage() { _actionMessage.value = null }

    init {
        viewModelScope.launch {
            authSessionManager.authState.collect { authState ->
                when (authState) {
                    AuthState.Initializing -> {
                        profileJob?.cancel()
                        _uiState.value = MineUiState.Loading
                    }
                    AuthState.LoggedOut -> {
                        profileJob?.cancel()
                        _uiState.value = MineUiState.LoggedOut
                    }
                    is AuthState.LoggedIn -> loadProfile(authState.userId)
                }
            }
        }
    }

    fun logout() {
        if (logoutJob?.isActive == true) return
        logoutJob = viewModelScope.launch {
            suspendRunCatching { authSessionManager.logout() }
                .onFailure { _actionMessage.value = errorReporter.message("退出登录失败", it) }
        }
    }

    fun refresh() {
        val authState = authSessionManager.authState.value
        if (authState is AuthState.LoggedIn) {
            loadProfile(authState.userId)
        }
    }

    private fun loadProfile(userId: Long) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            _uiState.value = MineUiState.Loading
            suspendRunCatching { mineRepository.getUserInfo(userId) }
                .onSuccess { _uiState.value = MineUiState.Content(it) }
                .onFailure {
                    _uiState.value = MineUiState.Error(errorReporter.message("个人资料加载失败", it))
                }
        }
    }

}
