package xyz.qiaosheng.bilibili.ui.video

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.error.InvalidResponseException
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.remote.dto.DashStream
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository
import xyz.qiaosheng.bilibili.data.repository.VideoRepository
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.offline.OfflineMediaContract
import xyz.qiaosheng.bilibili.offline.OfflinePlayable
import xyz.qiaosheng.bilibili.offline.OfflineRepository
import xyz.qiaosheng.bilibili.playback.EXTRA_QUALITY_ID
import xyz.qiaosheng.bilibili.playback.PlaybackHistoryMetadata
import xyz.qiaosheng.bilibili.playback.PlaybackService
import xyz.qiaosheng.bilibili.playback.awaitPlaybackController
import xyz.qiaosheng.bilibili.playback.resumePositionMillis
import xyz.qiaosheng.bilibili.ui.video.player.VideoQuality
import xyz.qiaosheng.bilibili.ui.video.player.preferredVideoStream
import xyz.qiaosheng.bilibili.ui.video.player.streamingMediaItem
import java.io.IOException
import javax.inject.Inject

/** 协调详情加载、播放服务连接和评论提交；播放本身由 PlaybackService 承载。 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val errorReporter: ErrorReporter,
    private val libraryRepository: LibraryRepository,
    private val offlineRepository: OfflineRepository,
    private val authSessionManager: AuthSessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<VideoUiState>>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val bvid: String = savedStateHandle["bvid"] ?: ""
    private val requestedCid: Long? = savedStateHandle.get<Long>("cid")?.takeIf { it > 0L }
    private val offlineRequested: Boolean = savedStateHandle["offline"] ?: false
    private var currentPage: VideoPageData? = null
    private var playbackAccountId = 0L
    private var initialPositionMs = 0L

    private val _player = MutableStateFlow<Player?>(null)
    val player = _player.asStateFlow()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var loadJob: Job? = null
    private var commentJob: Job? = null
    private val playbackListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val message = errorReporter.message("播放失败", error.cause ?: error)
            updateSuccess { it.copy(playbackErrorMessage = message) }
        }
    }

    private val aid = MutableStateFlow(0L)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    val comments = aid.flatMapLatest { currentAid ->
        if (currentAid > 0L) videoRepository.getCommentPager(currentAid)
        else flowOf(PagingData.empty())
    }
        .cachedIn(viewModelScope)
    private var videoStreams: Map<Int, DashStream> = emptyMap()
    private var audioStream: DashStream? = null

    init {
        if (bvid.isNotBlank()) {
            loadVideo()
        } else {
            _uiState.value = UiState.Error("缺少视频 BV 号")
        }
    }

    @OptIn(UnstableApi::class)
    fun loadVideo() {
        loadJob?.cancel()
        commentJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                playbackAccountId = currentAccountId()
                if (offlineRequested) {
                    val cached = offlineRepository.getPlayable(bvid, requestedCid)
                        ?: throw InvalidResponseException("缓存不完整或已删除，请到离线缓存页面重试")
                    openOffline(cached)
                    return@launch
                }
                val pageData = videoRepository.getVideoPage(bvid, requestedCid)
                val playInfo = videoRepository.getVideoPlayUrl(bvid, pageData.cid)
                val dash = playInfo.dash ?: throw InvalidResponseException("接口未返回 DASH 数据")
                videoStreams = dash.video
                    .groupBy { it.id }
                    .mapValues { (_, streams) -> streams.preferredVideoStream() }
                audioStream = dash.audio.maxByOrNull { it.bandwidth }
                    ?: throw InvalidResponseException("接口未返回音频流")
                val initialStream = videoStreams.values.maxByOrNull { it.id }
                    ?: throw InvalidResponseException("接口未返回视频流")

                requireCurrentAccount()
                currentPage = pageData
                initialPositionMs = savedPosition(pageData)
                aid.value = pageData.aid

                val controller = connectController()
                val currentItem = controller.currentMediaItem
                val existingQuality = currentItem?.requestMetadata?.extras
                    ?.getInt(EXTRA_QUALITY_ID)
                val resumeExisting = currentItem?.mediaId == bvid &&
                        PlaybackHistoryMetadata.read(currentItem)?.let {
                            it.accountId == playbackAccountId && it.video.cid == pageData.cid
                        } == true && !OfflineMediaContract.isOffline(currentItem) &&
                        controller.playbackState != Player.STATE_IDLE && controller.playbackState != Player.STATE_ENDED &&
                        controller.playerError == null && existingQuality in videoStreams
                val selectedStream = if (resumeExisting) {
                    videoStreams.getValue(existingQuality!!)
                } else initialStream
                _uiState.value = UiState.Success(
                    VideoUiState(
                        video = pageData.detail,
                        aid = pageData.aid,
                        cid = pageData.cid,
                        qualities = videoStreams.values
                            .sortedByDescending { it.id }
                            .map { VideoQuality(it.id, it.qualityName) },
                        selectedQualityId = selectedStream.id
                    )
                )
                // 重进同一视频时继续使用 Service 的进度和清晰度。
                setAudioOnly(false)
                if (!resumeExisting) setPlayerSource(selectedStream, keepPosition = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!offlineRequested) {
                    try {
                        // 远程不可用时仍能从完整缓存恢复；离线入口从一开始就不发网络请求。
                        val cached = offlineRepository.getPlayable(bvid, requestedCid)
                        if (cached != null) {
                            playbackAccountId = currentAccountId()
                            openOffline(cached)
                            return@launch
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (cacheError: Exception) {
                        errorReporter.message("读取离线视频失败", cacheError)
                    }
                }
                _uiState.value = UiState.Error(errorReporter.message("视频加载失败", exception))
            }
        }
    }

    private suspend fun openOffline(cached: OfflinePlayable) {
        requireCurrentAccount()
        currentPage = cached.page
        // 发送空分页会取消之前的评论请求，离线详情不依赖任何网络接口。
        aid.value = 0L
        videoStreams = emptyMap()
        audioStream = null
        val controller = connectController()
        val target = PlaybackHistoryMetadata.read(controller.currentMediaItem)
        val resumeExisting = target?.accountId == playbackAccountId &&
                target.video.bvid == bvid && target.video.cid == cached.page.cid &&
                controller.currentMediaItem?.let(OfflineMediaContract::isOffline) == true &&
                controller.playbackState != Player.STATE_IDLE && controller.playbackState != Player.STATE_ENDED &&
                controller.playerError == null
        _uiState.value = UiState.Success(
            VideoUiState(
                video = cached.page.detail,
                aid = cached.page.aid,
                cid = cached.page.cid,
                qualities = listOf(VideoQuality(cached.qualityId, cached.qualityLabel)),
                selectedQualityId = cached.qualityId,
                isOffline = true
            )
        )
        setAudioOnly(false)
        if (!resumeExisting) {
            val item =
                PlaybackHistoryMetadata.attach(cached.mediaItem, cached.page, playbackAccountId)
            controller.setMediaItem(item, savedPosition(cached.page))
            controller.prepare()
            controller.play()
        }
    }

    private suspend fun savedPosition(page: VideoPageData): Long = try {
        resumePositionMillis(
            libraryRepository.resumePosition(playbackAccountId, page.detail.bvid, page.cid),
            page.detail.duration.toLong()
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        errorReporter.message("读取观看进度失败", error)
        0L
    }

    private fun currentAccountId() =
        (authSessionManager.authState.value as? AuthState.LoggedIn)?.userId ?: 0L

    private fun requireCurrentAccount() {
        check(currentAccountId() == playbackAccountId) { "账号已切换，请重新打开视频" }
    }

    // 页面重试时复用已有连接，重连前解除旧控制器的监听和引用。
    private suspend fun connectController(): MediaController {
        (_player.value as? MediaController)?.takeIf { it.isConnected }?.let { return it }
        _player.value?.removeListener(playbackListener)
        controllerFuture?.let(MediaController::releaseFuture)
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (_player.value === controller) {
                    _player.value = null
                    _uiState.value = UiState.Error("播放服务已断开，请重试")
                }
            }
        }).buildAsync()
        controllerFuture = future
        return future.awaitPlaybackController().also {
            _player.value = it
            it.addListener(playbackListener)
        }
    }

    fun toggleBackgroundAudio() {
        val state = (_uiState.value as? UiState.Success)?.data ?: return
        runPlaybackAction {
            setAudioOnly(!state.isAudioOnly)
            if (!state.isAudioOnly) _player.value?.play()
        }
    }

    private fun setAudioOnly(audioOnly: Boolean) {
        val player = _player.value ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
        updateSuccess { it.copy(isAudioOnly = audioOnly) }
    }

    @OptIn(UnstableApi::class)
    fun selectQuality(qualityId: Int) {
        val state = (_uiState.value as? UiState.Success)?.data ?: return
        if (qualityId == state.selectedQualityId) return
        val stream = videoStreams[qualityId] ?: return
        runPlaybackAction {
            setPlayerSource(stream, keepPosition = true)
            updateSuccess { it.copy(selectedQualityId = qualityId) }
        }
    }

    fun retryPlayback() {
        runPlaybackAction {
            updateSuccess { it.copy(playbackErrorMessage = null) }
            _player.value?.prepare()
            _player.value?.play()
        }
    }

    private fun runPlaybackAction(action: () -> Unit) {
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val message = errorReporter.message("播放操作失败", exception)
            updateSuccess { it.copy(playbackErrorMessage = message) }
        }
    }

    fun updateCommentDraft(value: String) {
        updateSuccess { it.copy(commentDraft = value.take(MAX_COMMENT_LENGTH)) }
    }

    fun submitComment() {
        val state = (_uiState.value as? UiState.Success)?.data ?: return
        val message = state.commentDraft.trim()
        if (message.isEmpty() || state.commentSubmitting) return

        updateSuccess { it.copy(commentSubmitting = true, commentMessage = null) }
        val currentAid = aid.value
        commentJob = viewModelScope.launch {
            try {
                videoRepository.addComment(currentAid, message)
                updateSuccess { current ->
                    current.copy(
                        video = current.video.copy(replyCount = current.video.replyCount + 1),
                        totalCommentCount = current.totalCommentCount + 1,
                        commentDraft = "",
                        commentSubmitting = false,
                        commentMessage = "评论发送成功",
                        commentsRefreshVersion = current.commentsRefreshVersion + 1
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val errorMessage = errorReporter.message("评论发送失败", exception)
                updateSuccess {
                    it.copy(
                        commentMessage = if (exception is IOException) {
                            "$errorMessage；发送结果未确认，请先查看评论再决定是否重发"
                        } else errorMessage
                    )
                }
            } finally {
                // loadVideo 会先取消评论任务；其新状态不应被旧任务覆盖。
                if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    updateSuccess { it.copy(commentSubmitting = false) }
                }
            }
        }
    }

    fun consumeCommentMessage() {
        updateSuccess { it.copy(commentMessage = null) }
    }

    @OptIn(UnstableApi::class)
    private fun setPlayerSource(videoStream: DashStream, keepPosition: Boolean) {
        val player = _player.value ?: return
        val page = currentPage ?: return
        requireCurrentAccount()
        val audio = audioStream ?: throw InvalidResponseException("接口未返回音频流")
        val position =
            if (keepPosition) player.currentPosition.coerceAtLeast(0L) else initialPositionMs
        val shouldPlay = if (keepPosition) player.playWhenReady else true
        val item = streamingMediaItem(page, videoStream, audio, playbackAccountId)
        player.setMediaItem(item, position)
        player.prepare()
        player.playWhenReady = shouldPlay
    }

    private fun updateSuccess(transform: (VideoUiState) -> VideoUiState) {
        _uiState.update { state ->
            if (state is UiState.Success) UiState.Success(transform(state.data)) else state
        }
    }

    override fun onCleared() {
        // 只断开控制器；后台 ExoPlayer 由 PlaybackService.onDestroy 释放。
        _player.value?.removeListener(playbackListener)
        _player.value = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onCleared()
    }

    fun pausePlayback() {
        val state = (_uiState.value as? UiState.Success)?.data
        if (state?.isAudioOnly == true) return // 开了后台听，允许继续
        _player.value?.pause()
    }

    private companion object {
        const val MAX_COMMENT_LENGTH = 1000
    }
}

