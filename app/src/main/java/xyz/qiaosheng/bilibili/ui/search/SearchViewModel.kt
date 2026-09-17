package xyz.qiaosheng.bilibili.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.data.repository.SearchRepository

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var clearJob: Job? = null
    private val historyRetry = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchHistoryList = historyRetry.flatMapLatest {
        flow { emitAll(searchRepository.getSearchHistory()) }
            .onEach { _uiState.update { it.copy(historyErrorMessage = null) } }
            .catch { error ->
                if (error !is Exception) throw error
                val message = errorReporter.message("读取搜索历史失败", error)
                _uiState.update { it.copy(historyErrorMessage = message) }
                // 明确显示历史读取失败，并保留重新订阅入口。
                emit(emptyList())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retrySearchHistory() { historyRetry.update { it + 1 } }

    fun onSearchValueChange(query: String) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = query, isSearching = false, hasSearched = false,
                results = emptyList(), errorMessage = null)
        }
    }

    fun performSearch() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(query = query, isSearching = true, hasSearched = false, errorMessage = null)
            }
            suspendRunCatching { searchRepository.search(query).distinctBy { it.bvid } }
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(results = results, isSearching = false, hasSearched = true)
                    }
                }
                .onFailure { error ->
                    val message = errorReporter.message("搜索失败", error)
                    _uiState.update { it.copy(isSearching = false, errorMessage = message) }
                }
            // 取消的旧任务不写 finally 状态，避免关闭新请求的加载指示。
        }
    }

    fun searchHistoryClear() {
        if (clearJob?.isActive == true) return
        clearJob = viewModelScope.launch {
            suspendRunCatching { searchRepository.clear() }
                .onSuccess { _uiState.update { it.copy(historyActionMessage = null) } }
                .onFailure { error ->
                    val message = errorReporter.message("清空搜索历史失败", error)
                    _uiState.update { it.copy(historyActionMessage = message) }
                }
        }
    }

    fun deleteSearchHistory(keyword: String) {
        viewModelScope.launch {
            suspendRunCatching { searchRepository.delete(keyword) }
                .onSuccess { _uiState.update { it.copy(historyActionMessage = null) } }
                .onFailure { error ->
                    val message = errorReporter.message("删除搜索历史失败", error)
                    _uiState.update { it.copy(historyActionMessage = message) }
                }
        }
    }

    fun dismissHistoryActionMessage() {
        _uiState.update { it.copy(historyActionMessage = null) }
    }
}
