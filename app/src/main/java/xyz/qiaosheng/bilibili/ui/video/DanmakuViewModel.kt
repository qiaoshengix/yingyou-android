package xyz.qiaosheng.bilibili.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.local.preferences.PlayerPreferencesStore
import xyz.qiaosheng.bilibili.data.remote.DanmakuDisabledException
import xyz.qiaosheng.bilibili.data.repository.DanmakuRepository
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.model.danmaku.DANMAKU_SEGMENT_DURATION_MS
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem
import xyz.qiaosheng.bilibili.model.danmaku.danmakuSegmentIndex
import xyz.qiaosheng.bilibili.playback.PlaybackHistoryMetadata

/** 以播放器位置为准管理一个小窗口；请求、错误和开关均不改变视频播放状态。 */
@HiltViewModel
class DanmakuViewModel internal constructor(
    private val loadSegment: suspend (Long, Long, Int) -> List<DanmakuItem>,
    private val preferences: PlayerPreferencesStore,
    private val authState: StateFlow<AuthState>,
    private val sessionVersion: StateFlow<Long>,
    private val errorReporter: ErrorReporter,
) : ViewModel() {
    @Inject
    constructor(
        repository: DanmakuRepository,
        preferences: PlayerPreferencesStore,
        auth: AuthSessionManager,
        errorReporter: ErrorReporter,
    ) : this(repository::loadSegment, preferences, auth.authState, auth.sessionVersion, errorReporter)

    private val _state = MutableStateFlow(DanmakuUiState())
    val state = _state.asStateFlow()
    private var binding: Binding? = null
    private var active = false
    private var generation = 0L
    private var requestId = 0L
    private var session = currentSession()
    private var ticker: Job? = null
    private var preferenceRead: Job? = null
    private var preferenceLoading = true
    private var preferenceError: String? = null
    private var pendingSave: SaveRequest? = null
    private var saveRevision = 0L
    private val cached = mutableMapOf<Int, List<DanmakuItem>>()
    private val requests = mutableMapOf<Int, SegmentRequest>()
    private val failures = mutableMapOf<Int, SegmentFailure>()
    private var visibleSegments: Set<Int> = emptySet()
    private var currentSegment: Int? = null
    private var itemsDirty = false
    private var disabledReason: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (binding?.player === player) reconcile()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = reconcile()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = reconcile()
    }

    init {
        readPreference()
        viewModelScope.launch {
            combine(authState, sessionVersion) { state, version -> Session(state, version) }
                .collect { latest ->
                    if (latest != session) {
                        session = latest
                        disabledReason = null
                        discardSegments()
                        reconcile()
                    }
                }
        }
    }

    fun bind(
        player: Player?,
        aid: Long,
        cid: Long,
        durationSeconds: Long,
        isOffline: Boolean,
        isAudioOnly: Boolean,
    ) {
        val next = Binding(player, aid, cid, durationSeconds.coerceAtLeast(0), isOffline, isAudioOnly)
        if (binding == next) {
            reconcile()
            return
        }
        val previous = binding
        if (previous?.player !== player) {
            previous?.player?.removeListener(listener)
            player?.addListener(listener)
        }
        binding = next
        if (previous == null || previous.player !== player || previous.aid != aid ||
            previous.cid != cid || previous.isOffline != isOffline || previous.isAudioOnly != isAudioOnly
        ) {
            disabledReason = null
            discardSegments()
        }
        reconcile()
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (!active) {
            discardSegments()
        } else if (pendingSave == null) {
            // 从其它视频返回时重新读取全局播放习惯；全屏切换不会更改页面活跃状态。
            _state.update { it.copy(preferencesReady = false) }
            readPreference()
        }
        reconcile()
    }

    fun setEnabled(enabled: Boolean) {
        if (!_state.value.preferencesReady || _state.value.enabled == enabled) return
        _state.update { it.copy(enabled = enabled) }
        preferenceError = null
        savePreference(enabled)
        if (!enabled) discardSegments()
        reconcile()
    }

    fun retry() {
        if (!_state.value.preferencesReady) {
            readPreference()
            return
        }
        preferenceError = null
        pendingSave?.let { savePreference(it.enabled) }
        disabledReason = null
        failures.clear()
        reconcile()
    }

    /** 页面离开时释放 Player 引用；同一个 ViewModel 以后仍可重新绑定。 */
    fun close() {
        binding?.player?.removeListener(listener)
        binding = null
        active = false
        disabledReason = null
        discardSegments()
        publish()
    }

    override fun onCleared() {
        close()
    }

    private fun savePreference(enabled: Boolean) {
        val request = SaveRequest(++saveRevision, enabled)
        pendingSave = request
        // 必须在当前调用内同步入队，不能等 VM 的协程开始后再交给应用存续的 Store。
        val result = preferences.enqueueDanmakuEnabled(enabled)
        viewModelScope.launch {
            try {
                result.await()
                if (pendingSave?.revision == request.revision) {
                    pendingSave = null
                    preferenceError = null
                    publish()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (pendingSave?.revision == request.revision) {
                    preferenceError = errorReporter.message("保存弹幕开关失败", error)
                    publish()
                }
            }
        }
    }

    private fun readPreference() {
        if (preferenceRead?.isActive == true) return
        preferenceLoading = true
        preferenceError = null
        publish()
        preferenceRead = viewModelScope.launch {
            try {
                val enabled = preferences.danmakuEnabled.first()
                preferenceLoading = false
                _state.update { it.copy(enabled = enabled, preferencesReady = true) }
                reconcile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                preferenceLoading = false
                preferenceError = errorReporter.message("读取弹幕设置失败", error)
                publish()
            }
        }
    }

    private fun reconcile() {
        if (!canLoad()) {
            if (cached.isNotEmpty() || requests.isNotEmpty() || currentSegment != null) discardSegments()
            ticker?.cancel()
            ticker = null
            publish()
            return
        }
        val target = binding ?: return
        val player = target.player ?: return
        val durationMs = target.durationSeconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000
        val positionMs = player.currentPosition.coerceAtLeast(0)
            .let { if (durationMs > 0) it.coerceAtMost(durationMs - 1) else it }
        val index = danmakuSegmentIndex(positionMs)
        val elapsed = positionMs % DANMAKU_SEGMENT_DURATION_MS
        val visible = buildSet {
            add(index)
            if (index > 1 && elapsed < PREVIOUS_TAIL_MS) add(index - 1)
        }
        val desired = visible.toMutableSet().apply {
            val segmentEnd = index.toLong() * DANMAKU_SEGMENT_DURATION_MS
            if (DANMAKU_SEGMENT_DURATION_MS - elapsed <= PREFETCH_AHEAD_MS &&
                index < Int.MAX_VALUE && (durationMs == 0L || segmentEnd < durationMs)
            ) add(index + 1)
        }
        if (visibleSegments != visible) {
            visibleSegments = visible
            itemsDirty = true
        }
        currentSegment = index
        if (failures[index]?.disabled == true) {
            disabledReason = "该视频已关闭弹幕"
            discardSegments()
            return
        }
        // 跳转远处后，旧窗口立刻失效；迟到的旧请求还需通过 requestId 与会话检查。
        requests.keys.filter { it !in desired }.forEach { requests.remove(it)?.job?.cancel() }
        cached.keys.retainAll(desired)
        failures.keys.retainAll(desired)
        // 先请求当前段，再补前段尾部和预取下一段。
        desired.sortedBy { if (it == index) Int.MIN_VALUE else it }.forEach { segment ->
            if (segment !in cached && segment !in requests && segment !in failures) request(target, segment)
        }
        publish()
        if (player.isPlaying) {
            if (ticker?.isActive != true) {
                ticker = viewModelScope.launch {
                    while (isActive) {
                        delay(POSITION_POLL_MS)
                        reconcile()
                    }
                }
            }
        } else {
            // 暂停时仅响应 Player 的 seek / 状态事件，不空转轮询。
            ticker?.cancel()
            ticker = null
        }
    }

    private fun request(target: Binding, segment: Int) {
        if (!canLoad()) return
        val token = generation
        val id = ++requestId
        val capturedSession = currentSession()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val items = loadSegment(target.aid, target.cid, segment)
                if (isCurrentRequest(token, id, segment, capturedSession)) {
                    cached[segment] = items
                    if (segment in visibleSegments) itemsDirty = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentRequest(token, id, segment, capturedSession)) {
                    if (error is DanmakuDisabledException && segment == currentSegment) {
                        disabledReason = "该视频已关闭弹幕"
                        discardSegments()
                    } else {
                        failures[segment] = SegmentFailure(
                            message = errorReporter.message("加载弹幕失败", error),
                            disabled = error is DanmakuDisabledException,
                        )
                    }
                }
            } finally {
                if (isCurrentRequest(token, id, segment, capturedSession)) {
                    requests.remove(segment)
                }
                if (token == generation && capturedSession == currentSession()) publish()
            }
        }
        requests[segment] = SegmentRequest(id, job)
        job.start()
    }

    private fun isCurrentRequest(token: Long, id: Long, segment: Int, capturedSession: Session): Boolean =
        token == generation && requests[segment]?.id == id && capturedSession == currentSession() && canLoad()

    private fun canLoad(): Boolean {
        val target = binding ?: return false
        val player = target.player ?: return false
        if (!active || !_state.value.preferencesReady || !_state.value.enabled ||
            authState.value == AuthState.Initializing || unavailableReason() != null ||
            target.aid <= 0 || target.cid <= 0 || player.playbackState == Player.STATE_IDLE
        ) return false
        val playbackTarget = PlaybackHistoryMetadata.read(player.currentMediaItem)
        return playbackTarget == null ||
            (playbackTarget.video.aid == target.aid && playbackTarget.video.cid == target.cid &&
                playbackTarget.accountId == ((authState.value as? AuthState.LoggedIn)?.userId ?: 0L))
    }

    private fun unavailableReason(): String? = when {
        binding?.isOffline == true -> "离线播放暂不支持弹幕"
        binding?.isAudioOnly == true -> "后台听模式下不显示弹幕"
        else -> disabledReason
    }

    private fun discardSegments() {
        generation++
        ticker?.cancel()
        ticker = null
        val oldRequests = requests.values.toList()
        requests.clear()
        oldRequests.forEach { it.job.cancel() }
        cached.clear()
        failures.clear()
        visibleSegments = emptySet()
        currentSegment = null
        itemsDirty = true
        publish()
    }

    private fun publish() {
        val items = if (itemsDirty) {
            itemsDirty = false
            visibleSegments.flatMap { cached[it].orEmpty() }
                .distinctBy { it.id }
                .sortedWith(compareBy<DanmakuItem> { it.timeMs }.thenBy { it.id })
        } else _state.value.items
        _state.update {
            it.copy(
                items = items,
                loading = preferenceLoading || currentSegment?.let(requests::containsKey) == true,
                errorMessage = preferenceError ?: currentSegment?.let(failures::get)?.message,
                unavailableReason = unavailableReason(),
            )
        }
    }

    private fun currentSession() = Session(authState.value, sessionVersion.value)

    private data class Session(val auth: AuthState, val version: Long)
    private data class Binding(
        val player: Player?, val aid: Long, val cid: Long, val durationSeconds: Long,
        val isOffline: Boolean, val isAudioOnly: Boolean,
    )
    private data class SegmentRequest(val id: Long, val job: Job)
    private data class SegmentFailure(val message: String, val disabled: Boolean)
    private data class SaveRequest(val revision: Long, val enabled: Boolean)

    private companion object {
        const val POSITION_POLL_MS = 750L
        const val PREVIOUS_TAIL_MS = 16_000L
        const val PREFETCH_AHEAD_MS = 15_000L
    }
}
