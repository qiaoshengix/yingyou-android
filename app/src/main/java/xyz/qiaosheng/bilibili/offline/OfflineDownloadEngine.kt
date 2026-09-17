package xyz.qiaosheng.bilibili.offline

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.remote.network.BiliHeaderInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineDownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineCache: OfflineCache,
    private val authSession: AuthSessionManager
) {
    internal val events = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val failureMessages = ConcurrentHashMap<String, String>()
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor = Executors.newFixedThreadPool(3)

    internal val manager: DownloadManager by lazy {
        check(Looper.myLooper() == Looper.getMainLooper()) { "DownloadManager must be created on the main thread" }
        val index = DefaultDownloadIndex(offlineCache.databaseProvider, "offline_downloads")
        // DownloadService 自动 resumeDownloads；先将持久队列停住，账号恢复后才逐项放行。
        index.getDownloads().use { cursor ->
            val restored = mutableListOf<Download>()
            while (cursor.moveToNext()) restored += cursor.download
            restored.filter { !it.isTerminalState && it.state != Download.STATE_REMOVING }
                .forEach { download ->
                    index.putDownload(
                        Download(
                            download.request, Download.STATE_STOPPED,
                            download.startTimeMs, download.updateTimeMs, download.contentLength,
                            STOP_ACCOUNT, Download.FAILURE_REASON_NONE, DownloadProgress().apply {
                                bytesDownloaded = download.bytesDownloaded
                                percentDownloaded = download.percentDownloaded
                            })
                    )
                }
        }
        // CDN 请求只用正常播放接口返回的签名 URL；绝不携带可能被账号切换替换的全局 Cookie。
        val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(BiliHeaderInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val http = OkHttpDataSource.Factory(client)
        val upstream = DataSource.Factory {
            AccountGuardedDataSource(http.createDataSource()) { authSession.authState.value.offlineAccountId() }
        }
        DownloadManager(
            context,
            index,
            DefaultDownloaderFactory(offlineCache.downloadFactory(upstream), executor)
        ).apply {
            maxParallelDownloads = 2
            minRetryCount = 2
            addListener(object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    ready.complete(Unit)
                    events.tryEmit(Unit)
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    if (finalException != null) failureMessages[download.request.id] =
                        "下载失败，请重试以更新播放地址"
                    else if (download.state != Download.STATE_FAILED) failureMessages.remove(
                        download.request.id
                    )
                    events.tryEmit(Unit)
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download
                ) {
                    events.tryEmit(Unit)
                }
            })
            scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (currentDownloads.isNotEmpty()) events.tryEmit(Unit)
                }
            }
        }
    }

    internal suspend fun awaitReady() {
        withContext(Dispatchers.Main.immediate) { manager }
        ready.await()
    }

    internal suspend fun snapshot(): Map<String, Download> {
        awaitReady()
        val live =
            withContext(Dispatchers.Main.immediate) { manager.currentDownloads.associateBy { it.request.id } }
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, Download>()
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) result[cursor.download.request.id] = cursor.download
            }
            result.putAll(live)
            result
        }
    }

    internal companion object {
        const val STOP_USER = 1
        const val STOP_ACCOUNT = 2
    }
}
