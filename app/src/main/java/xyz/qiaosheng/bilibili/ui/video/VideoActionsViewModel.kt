package xyz.qiaosheng.bilibili.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository
import xyz.qiaosheng.bilibili.model.library.FavoriteFolder
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.model.library.VideoLibraryStatus
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.offline.OfflineRepository

data class VideoActionsState(
    val status: VideoLibraryStatus = VideoLibraryStatus(accountId = null),
    val busy: Boolean = false,
    val showFolders: Boolean = false,
    val foldersLoading: Boolean = false,
    val folders: List<FavoriteFolder> = emptyList(),
    val foldersError: String? = null,
    val message: String? = null
)

/** 收藏/点赞状态只订阅资料库；页面返回列表后，两处都会看到同一个本地结果。 */
@HiltViewModel
class VideoActionsViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val offline: OfflineRepository,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _state = MutableStateFlow(VideoActionsState())
    val state = _state.asStateFlow()
    private var page: VideoPageData? = null
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var generation = 0L
    private var folderRequest = 0L

    fun bind(page: VideoPageData) {
        if (this.page?.detail?.bvid == page.detail.bvid && this.page?.cid == page.cid) return
        this.page = page
        generation++
        observeJob?.cancel()
        refreshJob?.cancel()
        _state.value = VideoActionsState()
        observeJob = viewModelScope.launch {
            library.observeVideoStatus(page.detail.bvid).catch { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(message = errorReporter.message("读取本地操作状态失败", error)) }
            }.collect { status ->
                // generation 的副作用放在 CAS update 外，避免更新函数被重试时重复递增。
                if (_state.value.status.accountId != status.accountId) {
                    generation++
                    _state.value = VideoActionsState(status = status)
                    refreshStatus(page)
                } else {
                    _state.update { it.copy(status = status) }
                }
            }
        }
    }

    private fun refreshStatus(page: VideoPageData) {
        refreshJob?.cancel()
        if (_state.value.status.accountId == null) return
        refreshJob = viewModelScope.launch {
            try {
                library.refreshVideoStatus(page.asLibraryVideo())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 网络不可用时继续显示本地状态；资料库会保留具体同步错误。
                errorReporter.message("更新视频操作状态失败", error)
            }
        }
    }

    fun toggleLike() {
        val desired = !_state.value.status.liked
        runAccountAction("已保存点赞状态，将自动同步") { accountId, video ->
            library.setLiked(video, desired, accountId)
        }
    }

    fun openFolders() {
        if (_state.value.status.accountId == null) {
            _state.update { it.copy(message = "登录后可以收藏视频") }
            return
        }
        val accountId = _state.value.status.accountId
        val token = generation
        val request = ++folderRequest
        _state.update { it.copy(showFolders = true, foldersLoading = true, foldersError = null) }
        viewModelScope.launch {
            try {
                val folders = library.favoriteFolders()
                if (generation == token && folderRequest == request && _state.value.status.accountId == accountId) {
                    _state.update { it.copy(folders = folders, foldersLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == token && folderRequest == request && _state.value.status.accountId == accountId) {
                    _state.update { it.copy(foldersLoading = false,
                        foldersError = errorReporter.message("读取收藏夹失败", error)) }
                }
            }
        }
    }

    fun toggleFolder(folder: FavoriteFolder) {
        val desired = folder.id !in _state.value.status.favoriteFolderIds
        runAccountAction("已保存收藏状态，将自动同步") { accountId, video ->
            library.setFavorite(video, folder.id, desired, accountId)
        }
    }

    fun closeFolders() { _state.update { it.copy(showFolders = false) } }

    fun download(qualityId: Int) {
        val currentPage = page ?: return
        if (_state.value.busy) return
        val token = generation
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                offline.enqueue(currentPage, qualityId)
                if (generation == token) _state.update {
                    it.copy(message = "已加入缓存队列，可在「我的—离线缓存」查看进度")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == token) _state.update {
                    it.copy(message = errorReporter.message("添加离线缓存失败", error))
                }
            } finally {
                if (generation == token) _state.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }

    private fun runAccountAction(message: String, action: suspend (Long, LibraryVideo) -> Unit) {
        val currentPage = page ?: return
        if (_state.value.busy) return
        val accountId = _state.value.status.accountId
        val token = generation
        if (accountId == null) {
            _state.update { it.copy(message = "登录后可以点赞和收藏视频") }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action(accountId, currentPage.asLibraryVideo())
                if (generation == token) _state.update { it.copy(message = message) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == token && _state.value.status.accountId == accountId) {
                    _state.update { it.copy(message = errorReporter.message("保存操作失败", error)) }
                }
            } finally {
                if (generation == token && _state.value.status.accountId == accountId) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }
}

private fun VideoPageData.asLibraryVideo() = LibraryVideo(
    bvid = detail.bvid, aid = aid, cid = cid, title = detail.title,
    coverUrl = detail.coverUrl, ownerName = detail.owner.name,
    durationSeconds = detail.duration.toLong()
)
