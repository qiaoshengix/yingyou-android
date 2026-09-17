package xyz.qiaosheng.bilibili.playback

import androidx.core.content.ContextCompat.registerReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.offline.OfflineMediaSourceFactory

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService: MediaSessionService() {

    @Inject
    lateinit var dataSourceFactory: DataSource.Factory

    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var authSessionManager: AuthSessionManager
    @Inject lateinit var errorReporter: ErrorReporter
    @Inject lateinit var offlineMediaSourceFactory: OfflineMediaSourceFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyRecorder: PlaybackHistoryRecorder? = null

    private var mediaSession: MediaSession? = null

    private var noisyReceiver: AudioBecomingNoisyReceiver? = null

    private var isNoisyReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(VideoMediaSourceFactory(mediaSourceFactory, offlineMediaSourceFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
//            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()

        historyRecorder = PlaybackHistoryRecorder(player, libraryRepository, errorReporter)
        serviceScope.launch {
            authSessionManager.authState.collect { auth ->
                if (auth == AuthState.Initializing) return@collect
                val accountId = (auth as? AuthState.LoggedIn)?.userId ?: 0L
                val playingAccount = PlaybackHistoryMetadata.read(player.currentMediaItem)?.accountId
                if (playingAccount != null && playingAccount != accountId) {
                    // 退出或换号后停止旧账号会话，避免后台播放继续使用其数据。
                    historyRecorder?.flush()
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    // Controller 的 LocalConfiguration 不保证传到服务端，从请求元数据还原 URI。
                    return Futures.immediateFuture(mediaItems.map { item ->
                        val target = requireNotNull(PlaybackHistoryMetadata.read(item)) { "缺少播放记录信息" }
                        val accountId = (authSessionManager.authState.value as? AuthState.LoggedIn)?.userId ?: 0L
                        require(target.accountId == accountId) { "账号已切换，请重新打开视频" }
                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                        requireNotNull(uri) { "缺少播放地址" }
                        item.buildUpon().setUri(uri).build()
                    })
                }
            })
            .build()

        player.addListener(
            object : Player.Listener{
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        registerAudioBecomingNoisyReceiver(player)
                    } else {
                        unregisterAudioBecomingNoisyReceiver()
                    }
                }
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.packageName == packageName || controllerInfo.isTrusted) mediaSession else null

    override fun onDestroy() {
        historyRecorder?.close()
        historyRecorder = null
        serviceScope.cancel()
        // 1. 先注销广播接收器，防止泄露
        unregisterAudioBecomingNoisyReceiver()
        noisyReceiver = null

        // 2. 释放 MediaSession 与 Player
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * 注册耳机拔出广播
     */
    private fun registerAudioBecomingNoisyReceiver(player: Player) {
        // 如果已经注册过了，直接返回，避免重复注册
        if (isNoisyReceiverRegistered) return

        if (noisyReceiver == null) {
            noisyReceiver = AudioBecomingNoisyReceiver() {
                // 当拔出耳机触发广播时，如果还在播放，则暂停
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }

        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        // 使用 ContextCompat 注册，显式指定 RECEIVER_NOT_EXPORTED
        registerReceiver(
            this,
            noisyReceiver!!,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        isNoisyReceiverRegistered = true
    }

    /**
     * 注销耳机拔出广播
     */
    private fun unregisterAudioBecomingNoisyReceiver() {
        // 只有当已经注册过时，才执行注销操作
        if (isNoisyReceiverRegistered && noisyReceiver != null) {
            unregisterReceiver(noisyReceiver)
            isNoisyReceiverRegistered = false
        }
    }

}
