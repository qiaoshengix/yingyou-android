package xyz.qiaosheng.bilibili.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    private var openedKind: LibraryKind? = null
    private var requestedFolderId: Long? = null
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var appendJob: Job? = null
    private var mutationJob: Job? = null

    fun open(kind: LibraryKind) {
        if (openedKind == kind) return
        openedKind = kind
        requestedFolderId = null
        observeLibrary()
    }

    fun selectFolder(folderId: Long) {
        if (openedKind != LibraryKind.FAVORITES ||
            _uiState.value.snapshot?.selectedFolderId == folderId
        ) return
        requestedFolderId = folderId
        observeLibrary()
    }

    private fun observeLibrary() {
        val kind = openedKind ?: return
        val folderId = requestedFolderId
        observeJob?.cancel()
        observeJob = null
        refreshJob?.cancel()
        appendJob?.cancel()
        _uiState.value = LibraryUiState(
            kind = kind,
            operationInProgress = mutationJob?.isActive == true
        )
        observeJob = viewModelScope.launch {
            var firstSnapshot = true
            var observedAccountId: Long? = null
            suspendRunCatching {
                repository.observe(kind, folderId).collect { snapshot ->
                    val accountChanged = !firstSnapshot && observedAccountId != snapshot.accountId
                    _uiState.update {
                        it.copy(snapshot = snapshot, initialLoading = false)
                    }
                    // 先呈现本地快照，再刷新网络。账号变化时重新刷新对应账号的列表。
                    if (firstSnapshot || accountChanged) {
                        firstSnapshot = false
                        observedAccountId = snapshot.accountId
                        refreshJob?.cancel()
                        refreshJob = null
                        appendJob?.cancel()
                        refresh()
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(initialLoading = false, requestError = error.toUserMessage())
                }
            }
        }
    }

    fun refresh() {
        val kind = openedKind ?: return
        if (observeJob?.isCompleted == true) {
            observeLibrary()
            return
        }
        if (refreshJob?.isActive == true) return
        appendJob?.cancel()
        val folderId = requestedFolderId
        _uiState.update { it.copy(requestError = null) }
        refreshJob = viewModelScope.launch {
            suspendRunCatching { repository.refresh(kind, folderId) }
                .onFailure { error ->
                    _uiState.update { it.copy(requestError = error.toUserMessage()) }
                }
        }
    }

    fun loadMore(retry: Boolean = false) {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        if (!snapshot.hasMore || snapshot.loading || snapshot.loadingMore ||
            refreshJob?.isActive == true || appendJob?.isActive == true ||
            (!retry && (snapshot.error != null || state.requestError != null))
        ) return
        val folderId = requestedFolderId
        _uiState.update { it.copy(requestError = null) }
        appendJob = viewModelScope.launch {
            suspendRunCatching { repository.loadMore(state.kind, folderId) }
                .onFailure { error ->
                    _uiState.update { it.copy(requestError = error.toUserMessage()) }
                }
        }
    }

    fun sync() {
        if (mutationJob?.isActive == true) return
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            try {
                suspendRunCatching { repository.sync() }
                    .onSuccess { refresh() }
                    .onFailure { error ->
                        _uiState.update { it.copy(actionMessage = error.toUserMessage()) }
                    }
            } finally {
                _uiState.update { it.copy(operationInProgress = false) }
            }
        }
    }

    fun remove(video: LibraryVideo, accountId: Long?) {
        if (mutationJob?.isActive == true) return
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        if (snapshot.accountId != accountId) return
        val folderId = video.folderId ?: snapshot.selectedFolderId
        if (state.kind == LibraryKind.FAVORITES && folderId == null) {
            _uiState.update { it.copy(actionMessage = "请先选择收藏夹") }
            return
        }
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true) }
            try {
                suspendRunCatching {
                    // 0 表示访客身份，防止旧页面事件在切换账号后误改新账号数据。
                    val expectedAccountId = accountId ?: 0L
                    when (state.kind) {
                        LibraryKind.HISTORY -> repository.deleteHistory(video, expectedAccountId)
                        LibraryKind.FAVORITES -> repository.setFavorite(
                            video, requireNotNull(folderId), false, expectedAccountId
                        )
                        LibraryKind.LIKES -> repository.setLiked(video, false, expectedAccountId)
                    }
                }.onSuccess {
                    val message = when (state.kind) {
                        LibraryKind.HISTORY -> "已删除观看记录"
                        LibraryKind.FAVORITES -> "已取消收藏"
                        LibraryKind.LIKES -> "已取消点赞"
                    }
                    _uiState.update {
                        if (it.kind == state.kind && it.snapshot?.accountId == accountId) {
                            it.copy(actionMessage = message)
                        } else it
                    }
                }.onFailure { error ->
                    _uiState.update {
                        if (it.kind == state.kind && it.snapshot?.accountId == accountId) {
                            it.copy(actionMessage = error.toUserMessage())
                        } else it
                    }
                }
            } finally {
                _uiState.update { it.copy(operationInProgress = false) }
            }
        }
    }

    fun dismissActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
