package xyz.qiaosheng.bilibili.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authSessionManager: AuthSessionManager,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _startupState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val startupState = _startupState.asStateFlow()
    private var startupJob: Job? = null

    init { initialize() }

    fun initialize() {
        if (startupJob?.isActive == true) return
        startupJob = viewModelScope.launch {
            _startupState.value = UiState.Loading
            suspendRunCatching { authSessionManager.initialize() }
                .onSuccess { _startupState.value = UiState.Success(Unit) }
                .onFailure {
                    _startupState.value = UiState.Error(errorReporter.message("读取登录状态失败", it))
                }
        }
    }
}
