package xyz.qiaosheng.bilibili.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.data.repository.AuthRepository
import xyz.qiaosheng.bilibili.model.auth.LoginStatus

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<LoginUiState>>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var loginJob: Job? = null

    init {
        loadQrCode()
    }

    fun loadQrCode() {

        loginJob?.cancel()

        loginJob = viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                val qrData = authRepository.generateQrCode()

                _uiState.value = UiState.Success(
                    LoginUiState(
                        qrBitmap = generateQrCodeBitmap(qrData.url),
                        loginStatus = LoginStatus.Waiting
                    )
                )

                // 获取 key 后，由 ViewModel 直接开始轮询
                pollQrCode(qrData.qrcodeKey)
            } catch (e: CancellationException) {
                // 协程取消必须继续抛出
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(errorReporter.message("登录请求失败", e))
            }
        }
    }

    private suspend fun pollQrCode(qrcodeKey: String) {
        while (currentCoroutineContext().isActive) {
            val status = authRepository.pollQrCode(qrcodeKey)

            updateLoginStatus(status)

            when (status) {
                LoginStatus.Success,
                LoginStatus.Expired,
                LoginStatus.Failed -> return

                LoginStatus.Waiting,
                LoginStatus.Scanned -> delay(5_000.milliseconds)
            }
        }


    }
    private fun updateLoginStatus(status: LoginStatus) {
        val currentState = _uiState.value

        if (currentState is UiState.Success) {
            _uiState.value = UiState.Success(
                currentState.data.copy(
                    loginStatus = status
                )
            )
        }
    }
}
