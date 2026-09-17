package xyz.qiaosheng.bilibili.offline

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.repository.VideoRepository
import xyz.qiaosheng.bilibili.model.offline.OfflineStatus
import xyz.qiaosheng.bilibili.model.offline.OfflineVideo
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.offline.data.OfflineDao
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineDao,
    private val gson: Gson,
    private val videos: VideoRepository,
    private val authSession: AuthSessionManager,
    private val engine: OfflineDownloadEngine,
    private val cache: OfflineCache
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutation = Mutex()
    private val settings = context.getSharedPreferences("offline-settings", Context.MODE_PRIVATE)
    private var appVisible = false
    private val _wifiOnly = MutableStateFlow(settings.getBoolean("wifi-only", true))
    val wifiOnly = _wifiOnly.asStateFlow()

    init {
        scope.launch {
            try {
                authSession.initialize()
                engine.awaitReady()
                updateRequirements()
                authSession.authState.map { it.offlineAccountId() }.distinctUntilChanged()
                    .collectLatest { owner ->
                        // 先同步停止，包括正好还在等待 metadata I/O 的账号切换。
                        engine.manager.setStopReason(null, OfflineDownloadEngine.STOP_ACCOUNT)
                        if (owner != null && appVisible) restoreAccount(owner)
                        else engine.events.tryEmit(Unit)
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                engine.events.tryEmit(Unit)
            }
        }
        scope.launch {
            engine.events.collect {
                try {
                    syncRows()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) { /* 下一次进度事件/打开列表会重试；查询错误仍由 observeDownloads 交给 UI。 */
                }
            }
        }
    }

    /** Activity 可见时恢复队列；后台进程初始化只加载并停止任务，不自行启动前台服务。 */
    suspend fun onAppForeground() {
        withContext(Dispatchers.Main.immediate) { appVisible = true }
        val owner = account()
        engine.awaitReady()
        withContext(Dispatchers.Main.immediate) {
            if (!appVisible) return@withContext
            engine.manager.setStopReason(null, OfflineDownloadEngine.STOP_ACCOUNT)
            updateRequirements()
            engine.manager.resumeDownloads()
        }
        if (appVisible) restoreAccount(owner)
    }

    /** 已由用户启动的下载继续运行；后续账号变化需等待下次前台进入才恢复。 */
    fun onAppBackground() {
        appVisible = false
    }

    fun observeDownloads(): Flow<List<OfflineVideo>> = flow {
        authSession.initialize()
        emitAll(
            authSession.authState.map { it.offlineAccountId() }.distinctUntilChanged()
                .flatMapLatest { owner ->
                    if (owner == null) flowOf(emptyList())
                    else flow {
                        // Room 首次查询尚未返回时就清除上个账号的可见列表。
                        emit(emptyList())
                        emitAll(dao.observe(owner).map { rows -> rows.map { it.toModel() } })
                    }
                })
    }

    suspend fun enqueue(page: VideoPageData, qualityId: Int? = null): String {
        val owner = account()
        return mutation.withLock {
            requireAccount(owner)
            offlineRequire(page.cid > 0 && page.detail.bvid.isNotBlank()) { "视频信息不完整，无法创建缓存" }
            val existing = dao.find(owner, page.detail.bvid, page.cid)
            requireAccount(owner)
            if (existing != null) {
                offlineRequire(qualityId == null || qualityId == existing.qualityId) { "该视频已有缓存任务，请删除后更换清晰度" }
                if (existing.status == OfflineStatus.FAILED.name || existing.userPaused) refreshAndStart(
                    existing,
                    owner
                )
                return@withLock existing.id
            }
            val streams =
                selectOfflineStreams(videos.getVideoPlayUrl(page.detail.bvid, page.cid), qualityId)
            requireAccount(owner)
            val id = OfflineKeys.id(owner, page.detail.bvid, page.cid)
            val row = OfflineVideoEntity(
                id = id,
                accountId = owner,
                bvid = page.detail.bvid,
                cid = page.cid,
                pageJson = gson.toJson(page),
                qualityId = streams.video.id,
                qualityLabel = streams.video.qualityName,
                videoCodec = streams.video.codecs,
                videoBandwidth = streams.video.bandwidth,
                audioId = streams.audio.id,
                audioCodec = streams.audio.codecs,
                audioBandwidth = streams.audio.bandwidth,
                videoUrl = streams.video.baseUrl,
                audioUrl = streams.audio.baseUrl,
                videoKey = OfflineKeys.cacheKey(
                    id,
                    "video",
                    streams.video.id,
                    streams.video.codecs,
                    streams.video.bandwidth
                ),
                audioKey = OfflineKeys.cacheKey(
                    id,
                    "audio",
                    streams.audio.id,
                    streams.audio.codecs,
                    streams.audio.bandwidth
                ),
                createdAt = System.currentTimeMillis()
            )
            dao.put(row)
            try {
                start(row, owner)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                dao.updateProgress(id, OfflineStatus.FAILED.name, 0, -1, exception.toUserMessage())
                throw exception
            }
            id
        }
    }

    suspend fun pause(id: String) {
        val owner = account()
        mutation.withLock {
            requireAccount(owner)
            val row = requireOwned(id, owner)
            if (row.status == OfflineStatus.COMPLETED.name || row.deleting) return@withLock
            requireAccount(owner)
            dao.setPaused(id, owner, true, OfflineStatus.PAUSED.name)
            engine.awaitReady()
            withContext(Dispatchers.Main.immediate) {
                requireAccount(owner)
                engine.manager.setStopReason(row.videoRequestId, OfflineDownloadEngine.STOP_USER)
                engine.manager.setStopReason(row.audioRequestId, OfflineDownloadEngine.STOP_USER)
            }
        }
    }

    /** 继续也刷新签名地址，暂停几小时后仍能恢复已有字节。 */
    suspend fun resume(id: String) = retry(id)

    suspend fun retry(id: String) {
        val owner = account()
        mutation.withLock {
            requireAccount(owner)
            refreshAndStart(requireOwned(id, owner), owner)
        }
    }

    suspend fun remove(id: String) {
        val owner = account()
        mutation.withLock {
            requireAccount(owner)
            val row = requireOwned(id, owner)
            requireAccount(owner)
            dao.markRemoving(id, owner)
            engine.awaitReady()
            withContext(Dispatchers.Main.immediate) {
                requireAccount(owner)
                DownloadService.sendRemoveDownload(
                    context,
                    OfflineDownloadService::class.java,
                    row.videoRequestId,
                    true
                )
                DownloadService.sendRemoveDownload(
                    context,
                    OfflineDownloadService::class.java,
                    row.audioRequestId,
                    true
                )
            }
            engine.events.tryEmit(Unit)
        }
    }

    suspend fun setWifiOnly(value: Boolean) {
        withContext(Dispatchers.IO) {
            if (!settings.edit().putBoolean("wifi-only", value)
                    .commit()
            ) throw IOException("下载网络设置保存失败")
        }
        _wifiOnly.value = value
        engine.awaitReady()
        withContext(Dispatchers.Main.immediate) { updateRequirements() }
    }

    suspend fun getPlayable(bvid: String, cid: Long? = null): OfflinePlayable? {
        val owner = account()
        val row = dao.find(owner, bvid, cid) ?: return null
        if (row.status != OfflineStatus.COMPLETED.name) {
            syncRows()
            if (dao.get(row.id, owner)?.status != OfflineStatus.COMPLETED.name) return null
        }
        val complete =
            withContext(Dispatchers.IO) { cache.isComplete(row.videoKey) && cache.isComplete(row.audioKey) }
        requireAccount(owner)
        if (!complete) {
            dao.updateProgress(
                row.id,
                OfflineStatus.FAILED.name,
                row.downloadedBytes,
                row.totalBytes,
                "离线音视频缓存不完整，请重试"
            )
            return null
        }
        val page = gson.fromJson(row.pageJson, VideoPageData::class.java)
        return OfflinePlayable(
            page,
            OfflineMediaContract.mediaItem(page, row),
            row.qualityId,
            row.qualityLabel
        )
    }

    suspend fun getOfflinePage(bvid: String, cid: Long? = null): VideoPageData? =
        getPlayable(bvid, cid)?.page

    internal fun downloadManagerForService(): DownloadManager = engine.manager

    private suspend fun refreshAndStart(row: OfflineVideoEntity, owner: Long) {
        requireAccount(owner)
        offlineRequire(!row.deleting) { "正在删除该缓存" }
        if (row.status == OfflineStatus.COMPLETED.name && withContext(Dispatchers.IO) {
                cache.isComplete(row.videoKey) && cache.isComplete(row.audioKey)
            }) return
        try {
            val streams =
                selectOfflineStreams(videos.getVideoPlayUrl(row.bvid, row.cid), row.qualityId, row)
            requireAccount(owner)
            val snapshot = engine.snapshot()
            withContext(Dispatchers.IO) {
                listOf(
                    row.videoRequestId to row.videoKey,
                    row.audioRequestId to row.audioKey
                ).forEach { (request, key) ->
                    if (snapshot[request]?.state == Download.STATE_COMPLETED && !cache.isComplete(
                            key
                        )
                    ) cache.cache.removeResource(key)
                }
            }
            val refreshed = row.copy(
                videoUrl = streams.video.baseUrl, audioUrl = streams.audio.baseUrl,
                userPaused = false, status = OfflineStatus.QUEUED.name, errorMessage = null
            )
            requireAccount(owner)
            dao.put(refreshed)
            start(refreshed, owner)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            dao.updateProgress(
                row.id,
                OfflineStatus.FAILED.name,
                row.downloadedBytes,
                row.totalBytes,
                exception.toUserMessage()
            )
            throw exception
        }
    }

    private suspend fun start(row: OfflineVideoEntity, owner: Long) {
        engine.awaitReady()
        withContext(Dispatchers.Main.immediate) {
            requireAccount(owner)
            updateRequirements()
            engine.manager.resumeDownloads()
            DownloadService.sendAddDownload(
                context,
                OfflineDownloadService::class.java,
                row.request(false),
                true
            )
            DownloadService.sendAddDownload(
                context,
                OfflineDownloadService::class.java,
                row.request(true),
                true
            )
        }
    }

    private suspend fun restoreAccount(owner: Long) = mutation.withLock {
        if (!appVisible) return@withLock
        requireAccount(owner)
        val rows = dao.getAll()
        val snapshot = engine.snapshot()
        var resumes = false
        withContext(Dispatchers.Main.immediate) {
            requireAccount(owner)
            if (!appVisible) return@withContext
            rows.forEach { row ->
                if (row.deleting) {
                    engine.manager.removeDownload(row.videoRequestId)
                    engine.manager.removeDownload(row.audioRequestId)
                } else if (row.accountId == owner && !row.userPaused && row.status != OfflineStatus.FAILED.name &&
                    row.status != OfflineStatus.COMPLETED.name
                ) {
                    // 也覆盖元数据已写入、进程却在两个 addDownload 命令前退出的情况。
                    if (snapshot[row.videoRequestId] == null) engine.manager.addDownload(
                        row.request(
                            false
                        )
                    )
                    else engine.manager.setStopReason(row.videoRequestId, Download.STOP_REASON_NONE)
                    if (snapshot[row.audioRequestId] == null) engine.manager.addDownload(
                        row.request(
                            true
                        )
                    )
                    else engine.manager.setStopReason(row.audioRequestId, Download.STOP_REASON_NONE)
                    resumes = true
                }
            }
            if (resumes) {
                try {
                    DownloadService.startForeground(context, OfflineDownloadService::class.java)
                } catch (_: IllegalStateException) {
                    // 前后台切换窗口内被系统拒绝时保持可恢复队列，下一次 onStart 再解除全局暂停。
                    engine.manager.pauseDownloads()
                }
            }
        }
        engine.events.tryEmit(Unit)
    }

    private suspend fun syncRows() = mutation.withLock {
        val downloads = engine.snapshot()
        val (waiting, globallyPaused) = withContext(Dispatchers.Main.immediate) {
            (engine.manager.notMetRequirements != 0) to engine.manager.downloadsPaused
        }
        for (row in dao.getAll()) {
            val video = downloads[row.videoRequestId]
            val audio = downloads[row.audioRequestId]
            if (row.deleting && video == null && audio == null) {
                withContext(Dispatchers.IO) {
                    cache.cache.removeResource(row.videoKey)
                    cache.cache.removeResource(row.audioKey)
                }
                dao.delete(row.id)
                continue
            }
            val cached = withContext(Dispatchers.IO) {
                val videoLength = cache.length(row.videoKey)
                val audioLength = cache.length(row.audioKey)
                val full = cache.isComplete(row.videoKey) && cache.isComplete(row.audioKey)
                Triple(
                    full, cache.cachedBytes(row.videoKey) + cache.cachedBytes(row.audioKey),
                    if (videoLength > 0 && audioLength > 0) videoLength + audioLength else -1L
                )
            }
            val terminal =
                video?.state == Download.STATE_COMPLETED && audio?.state == Download.STATE_COMPLETED
            val full = cached.first && (terminal || row.status == OfflineStatus.COMPLETED.name)
            val broken = !cached.first && (terminal || row.status == OfflineStatus.COMPLETED.name)
            val failed =
                video?.state == Download.STATE_FAILED || audio?.state == Download.STATE_FAILED
            val status = when {
                row.deleting -> OfflineStatus.REMOVING
                full -> OfflineStatus.COMPLETED
                broken -> OfflineStatus.FAILED
                row.userPaused -> OfflineStatus.PAUSED
                failed -> OfflineStatus.FAILED
                row.status == OfflineStatus.FAILED.name -> OfflineStatus.FAILED
                row.accountId != authSession.authState.value.offlineAccountId() -> OfflineStatus.PAUSED
                globallyPaused -> OfflineStatus.PAUSED
                waiting -> OfflineStatus.WAITING_FOR_NETWORK
                video?.state == Download.STATE_DOWNLOADING || audio?.state == Download.STATE_DOWNLOADING -> OfflineStatus.DOWNLOADING
                else -> OfflineStatus.QUEUED
            }
            val error =
                if (broken) "离线音视频缓存不完整，请重试" else if (status == OfflineStatus.FAILED) {
                    engine.failureMessages[row.videoRequestId]
                        ?: engine.failureMessages[row.audioRequestId]
                        ?: row.errorMessage ?: "下载失败，请重试以更新播放地址"
                } else null
            if (status.name != row.status || cached.second != row.downloadedBytes || cached.third != row.totalBytes || error != row.errorMessage) {
                dao.updateProgress(row.id, status.name, cached.second, cached.third, error)
            }
        }
    }

    private fun updateRequirements() {
        engine.manager.requirements =
            Requirements(Requirements.NETWORK or if (_wifiOnly.value) Requirements.NETWORK_UNMETERED else 0)
        engine.events.tryEmit(Unit)
    }

    private suspend fun account(): Long {
        // 在等待 mutation 或其他 I/O 前固定本次调用的账号，不把排队中的 A 操作重新解释为 B。
        authSession.authState.value.offlineAccountId()?.let { return it }
        authSession.initialize()
        return offlineRequireNotNull(authSession.authState.value.offlineAccountId()) { "账号状态尚未就绪" }
    }

    private fun requireAccount(owner: Long) {
        offlineRequire(authSession.authState.value.offlineAccountId() == owner) { "账号已切换，请重试当前账号的操作" }
    }

    private suspend fun requireOwned(id: String, owner: Long): OfflineVideoEntity =
        offlineRequireNotNull(dao.get(id, owner)) { "当前账号没有这条离线缓存" }

    private fun OfflineVideoEntity.toModel(): OfflineVideo {
        val page = gson.fromJson(pageJson, VideoPageData::class.java)
        return OfflineVideo(
            id,
            accountId,
            bvid,
            cid,
            page.detail.title,
            page.detail.coverUrl,
            page.detail.owner.name,
            qualityId,
            qualityLabel,
            OfflineStatus.valueOf(status),
            downloadedBytes,
            totalBytes,
            errorMessage,
            createdAt
        )
    }

    private fun OfflineVideoEntity.request(audio: Boolean): DownloadRequest =
        DownloadRequest.Builder(
            if (audio) audioRequestId else videoRequestId,
            (if (audio) audioUrl else videoUrl).toUri()
        ).setMimeType(if (audio) MimeTypes.AUDIO_MP4 else MimeTypes.VIDEO_MP4)
            .setCustomCacheKey(if (audio) audioKey else videoKey)
            .build()
}
