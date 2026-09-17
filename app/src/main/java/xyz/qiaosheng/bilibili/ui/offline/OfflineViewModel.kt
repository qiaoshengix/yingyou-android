package xyz.qiaosheng.bilibili.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.model.offline.OfflineVideo
import xyz.qiaosheng.bilibili.offline.OfflineRepository

data class OfflineUiState(
    val downloads: List<OfflineVideo> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val message: String? = null,
    val busyIds: Set<String> = emptySet(),
    val wifiOnly: Boolean = true
)

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val repository: OfflineRepository,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null

    init {
        retryLoading()
        viewModelScope.launch { repository.wifiOnly.collect { wifi -> _uiState.update { it.copy(wifiOnly = wifi) } } }
    }

    fun retryLoading() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            try {
                repository.observeDownloads().collect { downloads ->
                    _uiState.update { it.copy(downloads = downloads, isLoading = false, loadError = null) }
                }
            } catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = errorReporter.message("读取离线缓存失败", exception)) }
            }
        }
    }

    fun pause(id: String) = action(id, "暂停缓存失败") { repository.pause(id) }
    fun resume(id: String) = action(id, "继续缓存失败") { repository.resume(id) }
    fun retry(id: String) = action(id, "重试缓存失败") { repository.retry(id) }
    fun remove(id: String) = action(id, "删除缓存失败") { repository.remove(id) }
    fun setWifiOnly(value: Boolean) = action("network-setting", "保存下载设置失败") { repository.setWifiOnly(value) }
    fun notificationDenied() { _uiState.update { it.copy(message = "通知未开启，缓存仍会继续；可在此页查看进度") } }
    fun dismissMessage() { _uiState.update { it.copy(message = null) } }

    private fun action(id: String, operation: String, block: suspend () -> Unit) {
        if (id in _uiState.value.busyIds) return
        _uiState.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            try { block() }
            catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) { _uiState.update { it.copy(message = errorReporter.message(operation, exception)) } }
            finally { _uiState.update { it.copy(busyIds = it.busyIds - id) } }
        }
    }
}
