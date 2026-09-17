package xyz.qiaosheng.bilibili.ui.video

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.local.preferences.PlayerPreferencesStore
import xyz.qiaosheng.bilibili.data.remote.DanmakuDisabledException
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class DanmakuViewModelTest {
    private val store = FakePreferencesDataStore()
    private lateinit var preferences: PlayerPreferencesStore
    private lateinit var applicationScope: CoroutineScope
    private val auth = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    private val version = MutableStateFlow(1L)
    private val player = FakePlayer()
    private val requests = mutableListOf<Request>()
    private var response: suspend (Request) -> List<DanmakuItem> = { listOf(item(it.segment)) }
    private lateinit var viewModel: DanmakuViewModel

    @Before fun setup() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        preferences = PlayerPreferencesStore(store, applicationScope)
    }

    @After fun teardown() {
        if (::viewModel.isInitialized) {
            viewModel.close()
            viewModel.viewModelScope.cancel()
        }
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    private fun create(offline: Boolean = false, audioOnly: Boolean = false) {
        viewModel = DanmakuViewModel(
            loadSegment = { aid, cid, segment ->
                val request = Request(aid, cid, segment)
                requests += request
                response(request)
            },
            preferences = preferences,
            authState = auth,
            sessionVersion = version,
            errorReporter = ErrorReporter { _, _ -> },
        )
        bind(offline = offline, audioOnly = audioOnly)
        viewModel.setActive(true)
    }

    private fun bind(cid: Long = 20, offline: Boolean = false, audioOnly: Boolean = false) {
        viewModel.bind(player.value, 10, cid, 7_200, offline, audioOnly)
    }

    @Test fun savedOffSettingIsReadBeforeAnyRequestAndRapidTogglesPersistTheLatestChoice() = runTest {
        preferences.setDanmakuEnabled(false)
        store.readGate = CompletableDeferred()
        create()
        runCurrent()
        assertFalse(viewModel.state.value.preferencesReady)
        assertTrue(requests.isEmpty())
        store.readGate!!.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.enabled)
        assertTrue(requests.isEmpty())

        store.writeDelayMs = 1_000
        viewModel.setEnabled(true)
        runCurrent()
        viewModel.setEnabled(false)
        viewModel.setEnabled(true)
        viewModel.setEnabled(false)
        assertFalse(viewModel.state.value.enabled)
        assertTrue(viewModel.state.value.items.isEmpty())
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enabled)
        assertFalse(preferences.danmakuEnabled.first())
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test fun failedPreferenceReadCanRetryWithoutSendingRequestsUsingTheDefault() = runTest {
        preferences.setDanmakuEnabled(false)
        store.readError = IOException("read unavailable")
        create()
        runCurrent()
        assertFalse(viewModel.state.value.preferencesReady)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(requests.isEmpty())
        store.readError = null
        viewModel.retry()
        runCurrent()
        assertTrue(viewModel.state.value.preferencesReady)
        assertFalse(viewModel.state.value.enabled)
        assertNull(viewModel.state.value.errorMessage)
        assertTrue(requests.isEmpty())
    }

    @Test fun latestQueuedChoiceSurvivesImmediatePageExitBeforeAnyWriteStarts() = runTest {
        create()
        runCurrent()
        store.writeDelayMs = 1_000
        viewModel.setEnabled(false)
        viewModel.setEnabled(true)
        viewModel.setEnabled(false)
        // 不运行已排队的协程，模拟点击后立即弹出页面并清除 ViewModel。
        viewModel.close()
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
        assertFalse(preferences.danmakuEnabled.first())
    }

    @Test fun nextPageWaitsForPendingWriteEvenWhenPreviousViewModelIsCancelled() = runTest {
        create()
        runCurrent()
        store.writeDelayMs = 1_000
        viewModel.setEnabled(false)
        runCurrent()
        viewModel.close()
        viewModel.viewModelScope.cancel()
        val previousRequests = requests.size
        create()
        runCurrent()
        assertFalse(viewModel.state.value.preferencesReady)
        assertEquals(previousRequests, requests.size)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enabled)
        assertTrue(viewModel.state.value.preferencesReady)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals(previousRequests, requests.size)
        assertFalse(preferences.danmakuEnabled.first())
    }

    @Test fun failedSaveKeepsTheImmediateChoiceAndRetryPersistsIt() = runTest {
        create()
        runCurrent()
        store.writeError = IOException("disk temporarily unavailable")
        viewModel.setEnabled(false)
        runCurrent()
        assertFalse(viewModel.state.value.enabled)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(preferences.danmakuEnabled.first())
        store.writeError = null
        viewModel.retry()
        runCurrent()
        assertFalse(viewModel.state.value.enabled)
        assertFalse(preferences.danmakuEnabled.first())
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test fun returningFromAnotherVideoUsesItsSavedSwitchBeforeLoadingAgain() = runTest {
        create()
        runCurrent()
        viewModel.setActive(false)
        preferences.setDanmakuEnabled(false)
        val previousRequests = requests.size
        viewModel.setActive(true)
        runCurrent()
        assertFalse(viewModel.state.value.enabled)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals(previousRequests, requests.size)
    }

    @Test fun offlineAndBackgroundAudioNeverFetchAndReturningToVideoLoadsAgain() = runTest {
        create(offline = true)
        runCurrent()
        assertTrue(requests.isEmpty())
        assertEquals("离线播放暂不支持弹幕", viewModel.state.value.unavailableReason)
        bind(audioOnly = true)
        runCurrent()
        assertTrue(requests.isEmpty())
        assertEquals("后台听模式下不显示弹幕", viewModel.state.value.unavailableReason)
        bind()
        runCurrent()
        assertEquals(listOf(1), requests.map { it.segment })
        assertEquals(listOf(item(1)), viewModel.state.value.items)
    }

    @Test fun seekWhilePausedKeepsOnlyTheNewWindowAndBoundaryTailWithoutPolling() = runTest {
        create()
        runCurrent()
        val readsWhenPaused = player.positionReads
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(readsWhenPaused, player.positionReads)
        assertEquals(listOf(1), requests.map { it.segment })

        player.seek(361_000)
        runCurrent()
        assertEquals(listOf(1, 2), requests.map { it.segment })
        assertEquals(setOf("segment-1", "segment-2"), viewModel.state.value.items.map { it.id }.toSet())
        player.seek(380_000)
        runCurrent()
        assertEquals(listOf("segment-2"), viewModel.state.value.items.map { it.id })
        player.seek(1_820_000)
        runCurrent()
        assertEquals(listOf(1, 2, 6), requests.map { it.segment })
        assertEquals(listOf("segment-6"), viewModel.state.value.items.map { it.id })
        assertFalse(player.playing)
    }

    @Test fun failedPrefetchLeavesCurrentCommentsVisibleAndOffersRetryAfterEnteringThatSegment() = runTest {
        player.position = 350_000
        response = { request ->
            if (request.segment == 2) throw IOException("segment temporarily unavailable")
            listOf(item(request.segment))
        }
        create()
        runCurrent()
        assertEquals(listOf(1, 2), requests.map { it.segment })
        assertEquals(listOf(item(1)), viewModel.state.value.items)
        assertNull(viewModel.state.value.errorMessage)

        player.seek(361_000)
        runCurrent()
        assertNotNull(viewModel.state.value.errorMessage)
        assertEquals(listOf(item(1)), viewModel.state.value.items)
        assertEquals(2, requests.size)
        response = { listOf(item(it.segment)) }
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(1, 2, 2), requests.map { it.segment })
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(2, viewModel.state.value.items.size)
    }

    @Test fun videosWithDanmakuDisabledDoNotRetryForever() = runTest {
        response = { throw DanmakuDisabledException() }
        create()
        runCurrent()
        assertEquals("该视频已关闭弹幕", viewModel.state.value.unavailableReason)
        assertFalse(viewModel.state.value.loading)
        repeat(4) { bind() }
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(viewModel.state.value.items.isEmpty())
        response = { listOf(item(it.segment)) }
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.state.value.unavailableReason)
        assertEquals(2, requests.size)
        assertEquals(1, viewModel.state.value.items.size)
    }

    @Test fun lateReplyCannotRestoreCommentsAfterSwitchOffOrChangingCid() = runTest {
        lateinit var oldReply: Continuation<List<DanmakuItem>>
        response = { suspendCoroutine { oldReply = it } }
        create()
        runCurrent()
        viewModel.setEnabled(false)
        oldReply.resume(listOf(item(1)))
        runCurrent()
        assertTrue(viewModel.state.value.items.isEmpty())
        assertNull(viewModel.state.value.errorMessage)

        response = { request ->
            if (request.cid == 20L) suspendCoroutine { oldReply = it }
            else listOf(item(request.segment).copy(id = "new-cid"))
        }
        viewModel.setEnabled(true)
        runCurrent()
        bind(cid = 21)
        runCurrent()
        assertEquals(listOf("new-cid"), viewModel.state.value.items.map { it.id })
        oldReply.resume(listOf(item(1)))
        runCurrent()
        assertEquals(listOf("new-cid"), viewModel.state.value.items.map { it.id })
    }

    @Test fun newSessionOfTheSameAccountRejectsLateOldReplyAndStopsWhenPageLeaves() = runTest {
        auth.value = AuthState.LoggedIn(42)
        lateinit var oldReply: Continuation<List<DanmakuItem>>
        response = { suspendCoroutine { oldReply = it } }
        create()
        runCurrent()
        response = { listOf(item(it.segment).copy(id = "new-session")) }
        version.value++
        runCurrent()
        assertEquals(listOf("new-session"), viewModel.state.value.items.map { it.id })
        oldReply.resume(listOf(item(1)))
        runCurrent()
        assertEquals(listOf("new-session"), viewModel.state.value.items.map { it.id })

        viewModel.setActive(false)
        assertTrue(viewModel.state.value.items.isEmpty())
        val requestCount = requests.size
        player.seek(1_820_000)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(requestCount, requests.size)
        viewModel.setActive(true)
        runCurrent()
        assertEquals(6, requests.last().segment)
        viewModel.close()
        assertTrue(player.listeners.isEmpty())
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test fun rebindingTheSamePlayerDoesNotRefetchAndPlayingPollingStopsOnPause() = runTest {
        create()
        runCurrent()
        repeat(4) { bind() }
        runCurrent()
        assertEquals(1, requests.size)
        player.changePlaying(true)
        player.position = 350_000
        advanceTimeBy(750)
        runCurrent()
        assertEquals(listOf(1, 2), requests.map { it.segment })
        player.changePlaying(false)
        val readsWhenPaused = player.positionReads
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(readsWhenPaused, player.positionReads)
        assertEquals(2, requests.size)
    }

    private data class Request(val aid: Long, val cid: Long, val segment: Int)

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val stored = MutableStateFlow<Preferences>(emptyPreferences())
        var readGate: CompletableDeferred<Unit>? = null
        var readError: IOException? = null
        var writeError: IOException? = null
        var writeDelayMs = 0L
        override val data: Flow<Preferences> = flow {
            readGate?.await()
            readError?.let { throw it }
            emitAll(stored)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeError?.let { throw it }
            delay(writeDelayMs)
            return transform(stored.value).also { stored.value = it }
        }
    }

    /** 仅替代 Player 的读接口和事件来源，窗口调度与协程取消继续运行生产代码。 */
    private class FakePlayer {
        var position = 0L
        var playing = false
        var positionReads = 0
        val listeners = mutableSetOf<Player.Listener>()
        val value: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position.also { positionReads++ }
                "isPlaying" -> playing
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentMediaItem" -> null
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "equals" -> proxy === args!![0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakePlayer"
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun seek(positionMs: Long) {
            val before = snapshot()
            position = positionMs
            val after = snapshot()
            listeners.toList().forEach { it.onPositionDiscontinuity(before, after, Player.DISCONTINUITY_REASON_SEEK) }
        }

        fun changePlaying(value: Boolean) {
            playing = value
            listeners.toList().forEach { it.onIsPlayingChanged(value) }
        }

        private fun snapshot() = Player.PositionInfo(null, 0, null, null, 0, position, position, -1, -1)
    }

    private companion object {
        fun item(segment: Int) = DanmakuItem(
            id = "segment-$segment", timeMs = segment.toLong() * 360_000 - 1_000,
            text = "弹幕 $segment", mode = 1, fontSize = 25, color = 0xffffff,
        )
    }
}
