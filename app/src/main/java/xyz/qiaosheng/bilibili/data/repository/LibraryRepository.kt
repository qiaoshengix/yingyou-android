package xyz.qiaosheng.bilibili.data.repository

import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.data.local.dao.LibraryDao
import xyz.qiaosheng.bilibili.data.local.entity.*
import xyz.qiaosheng.bilibili.data.remote.LibraryRemoteDataSource
import xyz.qiaosheng.bilibili.data.sync.*
import xyz.qiaosheng.bilibili.model.library.*

/** Room 是条目/收藏夹/待提交意图的唯一可观察数据源；网络结果只能经合并事务进入界面。 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val remote: LibraryRemoteDataSource,
    private val session: LibrarySession,
    private val scheduler: LibrarySyncScheduler,
    @LibraryScope private val scope: CoroutineScope
) {
    private data class Query(val account: Long, val kind: LibraryKind, val folder: Long = 0)
    private data class Loading(
        val loading: Boolean = false, val moreLoading: Boolean = false, val hasMore: Boolean = false,
        val cursor: String? = null, val generation: String = "", val startedAt: Long = 0,
        val error: String? = null, val loginRequired: Boolean = false
    )
    private val loads = MutableStateFlow<Map<Query, Loading>>(emptyMap())
    private val statusErrors = MutableStateFlow<Map<Pair<Long, String>, String>>(emptyMap())
    private val writers = Mutex()
    private val syncing = Mutex()
    private val pages = ConcurrentHashMap<Pair<Long, LibraryKind>, Mutex>()
    private val started = AtomicBoolean(false)
    private val orderingClock = AtomicLong()

    init { start() }

    /** 应用启动时强制构造此仓库即可恢复队列；登录后也会唤醒该账号的积压意图。 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try { session.initialize() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 启动错误由现有 MainViewModel 呈现；继续监听后续登录恢复。 */ }
            session.changes.collect { account ->
                if (account != null) {
                    writers.withLock { dao.retryBlocked(account, includePermanent = false) }
                    scheduler.enqueue(account)
                }
            }
        }
    }

    fun observe(kind: LibraryKind, folderId: Long? = null): Flow<LibrarySnapshot> = session.accountId.flatMapLatest { account ->
        val localAccount = account ?: 0L
        dao.observeFolders(localAccount).flatMapLatest { cachedFolders ->
            val selected = if (kind == LibraryKind.FAVORITES) folderId ?: cachedFolders.firstOrNull()?.folderId else null
            val key = Query(localAccount, kind, selected ?: 0)
            combine(dao.observeEntries(localAccount, kind.name, selected ?: 0), dao.observeOutbox(localAccount), loads) { entries, queue, states ->
                val loading = states[key] ?: states[Query(localAccount, kind)] ?: Loading()
                val pending = queue.filter { it.kind == kind.name && (kind != LibraryKind.FAVORITES || it.folderId == selected) }
                LibrarySnapshot(
                    items = entries.map { it.video() }, accountId = account,
                    loading = loading.loading, loadingMore = loading.moreLoading, hasMore = loading.hasMore,
                    error = loading.error ?: pending.firstNotNullOfOrNull { it.error }, pendingCount = pending.size,
                    folders = cachedFolders.map { FavoriteFolder(it.folderId, it.title, it.mediaCount) },
                    selectedFolderId = selected, remoteScopeNotice = if (kind == LibraryKind.LIKES) LIKES_SCOPE else null,
                    loginRequired = account == null && kind != LibraryKind.HISTORY || loading.loginRequired || pending.any { it.state == "LOGIN_REQUIRED" }
                )
            }
        }
    }

    suspend fun refresh(kind: LibraryKind, folderId: Long? = null) = load(kind, folderId, refresh = true)
    suspend fun loadMore(kind: LibraryKind, folderId: Long? = null) = load(kind, folderId, refresh = false)

    private suspend fun load(kind: LibraryKind, requestedFolder: Long?, refresh: Boolean) {
        val account = session.accountId.value ?: return
        pages.getOrPut(account to kind) { Mutex() }.withLock {
            var folder = if (kind == LibraryKind.FAVORITES) requestedFolder ?: dao.folders(account).firstOrNull()?.folderId ?: 0 else 0
            var key = Query(account, kind, folder)
            val old = loads.value[key] ?: Loading()
            if (!refresh && (!old.hasMore || old.loading || old.moreLoading)) return@withLock
            val began = if (refresh) nextChangeTime() else old.startedAt
            val generation = if (refresh) UUID.randomUUID().toString() else old.generation
            setLoad(key, old.copy(loading = refresh, moreLoading = !refresh, error = null, loginRequired = false))
            try {
                val credentials = session.credentials(account)
                if (kind == LibraryKind.FAVORITES && refresh) {
                    val folders = remote.folders(credentials)
                    writers.withLock {
                        requireCurrent(credentials)
                        dao.replaceFolders(account, folders.map { FavoriteFolderEntity(account, it.id, it.title.orEmpty(), it.mediaCount) })
                    }
                    folder = requestedFolder ?: dao.folders(account).firstOrNull()?.folderId ?: 0
                    val previousKey = key
                    key = Query(account, kind, folder)
                    setLoad(previousKey, Loading())
                    setLoad(key, old.copy(loading = true, error = null))
                    if (folder == 0L) { setLoad(key, Loading()); return@withLock }
                    require(folders.any { it.id == folder }) { "收藏夹已删除或不属于当前账号" }
                }
                val cursor = old.cursor.takeUnless { refresh }
                val page = when (kind) {
                    LibraryKind.HISTORY -> remote.history(credentials, cursor)
                    LibraryKind.FAVORITES -> remote.favorites(credentials, folder, cursor)
                    LibraryKind.LIKES -> remote.recentLikes(credentials)
                }
                // 最近列表可能滞后；逐项核实取消状态，既避免旧列表复活本地取消，也接受跨设备重新点赞。
                val verifiedLikes = if (kind == LibraryKind.LIKES) page.videos.mapNotNull { video ->
                    val current = dao.entry(account, kind.name, 0, video.bvid)
                    video.bvid.takeIf { current?.deleted == true && !current.pending &&
                        current.modifiedAt <= began && remote.liked(credentials, video.bvid) }
                }.toSet() else emptySet()
                writers.withLock {
                    requireCurrent(credentials)
                    val entries = page.videos.mapIndexed { index, video ->
                        video.entity(account, kind, folder, began, pending = false).copy(
                            sortAt = if (video.viewedAt > 0) video.viewedAt * 1000 else began - index)
                    }
                    dao.mergePage(account, kind.name, folder, entries, generation, began, !page.hasMore, verifiedLikes)
                }
                setLoad(key, Loading(hasMore = page.hasMore, cursor = page.nextCursor, generation = generation, startedAt = began))
            } catch (error: CancellationException) {
                setLoad(key, old.copy(loading = false, moreLoading = false))
                throw error
            } catch (error: Exception) {
                if (session.isCurrent(account)) setLoad(key, old.copy(loading = false, moreLoading = false,
                    error = message(error), loginRequired = error is LoginRequiredException))
            }
        }
    }

    suspend fun favoriteFolders(): List<FavoriteFolder> {
        val account = currentAccount()
        try {
            val credentials = session.credentials(account)
            val folders = remote.folders(credentials)
            writers.withLock {
                requireCurrent(credentials)
                dao.replaceFolders(account, folders.map { FavoriteFolderEntity(account, it.id, it.title.orEmpty(), it.mediaCount) })
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            requireCurrent(account)
            if (dao.folders(account).isEmpty()) throw error
        }
        requireCurrent(account)
        return dao.folders(account).map { FavoriteFolder(it.folderId, it.title, it.mediaCount) }
    }

    suspend fun recordWatch(accountId: Long, video: LibraryVideo, positionSeconds: Long, completed: Boolean = false) {
        require(video.bvid.isNotBlank() && video.cid > 0) { "观看记录缺少视频或分P编号" }
        writers.withLock {
            // 播放服务提交的是开启视频时捕获的账号；切号后迟到的周期回调直接丢弃。
            if (!session.isCurrent(accountId)) return
            val now = nextChangeTime()
            val progress = positionSeconds.coerceAtLeast(0).let { if (video.durationSeconds > 0) it.coerceAtMost(video.durationSeconds) else it }
            val watched = video.copy(progressSeconds = if (completed) -1 else progress, viewedAt = now / 1000)
            val entry = watched.entity(accountId, LibraryKind.HISTORY, 0, now, accountId != 0L)
            val operation = if (accountId != 0L) entry.operation(true, completed, now).copy(progressSeconds = progress) else null
            dao.mutate(entry, operation, WatchProgressEntity(accountId, video.bvid, video.cid, progress, completed, now))
        }
        if (accountId != 0L) scheduler.enqueue(accountId)
    }

    suspend fun resumePosition(accountId: Long, bvid: String, cid: Long): Long {
        if (!session.isCurrent(accountId)) return 0
        val value = dao.progress(accountId, bvid, cid) ?: return 0
        return if (session.isCurrent(accountId) && !value.completed) value.positionSeconds.coerceAtLeast(0) else 0
    }

    suspend fun setLiked(video: LibraryVideo, liked: Boolean, expectedAccountId: Long? = null) =
        mutate(video, LibraryKind.LIKES, 0, liked, currentAccount(expectedAccountId))

    suspend fun setFavorite(video: LibraryVideo, folderId: Long, favorite: Boolean, expectedAccountId: Long? = null) {
        val account = currentAccount(expectedAccountId)
        require(folderId > 0 && dao.folders(account).any { it.folderId == folderId }) { "请先选择当前账号的收藏夹" }
        mutate(video, LibraryKind.FAVORITES, folderId, favorite, account)
    }

    suspend fun deleteHistory(item: LibraryVideo, expectedAccountId: Long? = null) {
        val account = expectedAccountId ?: (session.accountId.value ?: 0L)
        requireCurrent(account)
        mutate(item, LibraryKind.HISTORY, 0, desired = false, account)
    }

    private suspend fun mutate(video: LibraryVideo, kind: LibraryKind, folder: Long, desired: Boolean, account: Long) {
        require(video.bvid.isNotBlank()) { "视频编号不能为空" }
        writers.withLock {
            requireCurrent(account)
            val now = nextChangeTime()
            val entry = video.entity(account, kind, folder, now, account != 0L).copy(deleted = !desired)
            dao.mutate(entry, if (account != 0L) entry.operation(desired, false, now) else null)
        }
        if (account != 0L) scheduler.enqueue(account)
    }

    fun observeVideoStatus(bvid: String): Flow<VideoLibraryStatus> = session.accountId.flatMapLatest { account ->
        if (account == null) flowOf(VideoLibraryStatus())
        else combine(dao.observeVideo(account, bvid), dao.observeOutbox(account), dao.observeFolders(account), statusErrors) { entries, queue, folders, errors ->
            val pending = queue.filter { it.bvid == bvid && it.kind != LibraryKind.HISTORY.name }
            VideoLibraryStatus(account, entries.any { it.kind == "LIKES" && !it.deleted },
                entries.filter { it.kind == "FAVORITES" && !it.deleted && folders.any { folder -> folder.folderId == it.folderId } }
                    .map { it.folderId }.toSet(),
                pending.size, errors[account to bvid] ?: pending.firstNotNullOfOrNull { it.error })
        }
    }

    suspend fun refreshVideoStatus(video: LibraryVideo) {
        val account = session.accountId.value ?: return
        val began = nextChangeTime()
        try {
            val credentials = session.credentials(account)
            val resolved = remote.resolve(credentials, video)
            val liked = remote.liked(credentials, video.bvid)
            val folders = remote.folders(credentials, resolved.aid.takeIf { it > 0 })
            require(resolved.aid > 0) { "视频缺少有效稿件编号" }
            writers.withLock {
                requireCurrent(credentials)
                val previousFolders = dao.folders(account)
                dao.replaceFolders(account, folders.map { FavoriteFolderEntity(account, it.id, it.title.orEmpty(), it.mediaCount) })
                suspend fun status(kind: LibraryKind, folder: Long, selected: Boolean) {
                    val current = dao.entry(account, kind.name, folder, video.bvid)
                    if (current == null || !current.pending && current.modifiedAt <= began) {
                        dao.putEntry(resolved.entity(account, kind, folder, began, false).copy(deleted = !selected))
                    }
                }
                status(LibraryKind.LIKES, 0, liked)
                folders.forEach { status(LibraryKind.FAVORITES, it.id, it.favState == 1) }
                previousFolders.filter { previous -> folders.none { it.id == previous.folderId } }
                    .forEach { status(LibraryKind.FAVORITES, it.folderId, false) }
            }
            statusErrors.update { it - (account to video.bvid) }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (session.isCurrent(account)) statusErrors.update { it + ((account to video.bvid) to message(error)) }
        }
    }

    suspend fun sync() {
        val account = session.accountId.value ?: return
        writers.withLock { dao.retryBlocked(account, includePermanent = true) }
        scheduler.enqueue(account)
        syncPending(account)
    }

    /** 返回 true 只表示暂时性失败；登录过期或永久失败保留队列并等待用户操作，避免无限后台重试。 */
    suspend fun syncPending(accountId: Long): Boolean = syncing.withLock {
        session.initialize()
        if (!session.isCurrent(accountId) || accountId == 0L) return@withLock false
        var retry = false
        for (operation in dao.outbox(accountId).filter { it.state == "PENDING" }) {
            if (!session.isCurrent(accountId)) break
            if (dao.outbox(accountId).none { it.mutationId == operation.mutationId }) continue
            var credentials: LibraryCredentials? = null
            try {
                credentials = session.credentials(accountId)
                remote.submit(credentials, operation)
                writers.withLock {
                    if (session.isCurrent(credentials)) dao.acknowledge(operation)
                    else if (session.isCurrent(accountId)) retry = true
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!session.isCurrent(accountId)) break
                // 同账号重新扫码也会换凭据；旧会话的 -101 不能再次封锁已被登录事件唤醒的队列。
                if (credentials != null && !session.isCurrent(credentials)) {
                    retry = true
                    break
                }
                val state = when {
                    error is LoginRequiredException -> "LOGIN_REQUIRED"
                    transient(error) -> "PENDING"
                    else -> "FAILED"
                }
                writers.withLock { dao.fail(operation, state, message(error)) }
                if (state == "PENDING") retry = true
                if (state == "LOGIN_REQUIRED") break
            }
        }
        retry
    }

    private fun currentAccount(expected: Long? = null): Long {
        val account = session.accountId.value ?: throw LoginRequiredException()
        if (expected != null && expected != account) throw LoginRequiredException()
        requireCurrent(account)
        return account
    }
    private fun requireCurrent(account: Long) { if (!session.isCurrent(account)) throw LoginRequiredException() }
    private fun requireCurrent(credentials: LibraryCredentials) { if (!session.isCurrent(credentials)) throw LoginRequiredException() }
    // 同一毫秒内的刷新和本地操作也必须有先后次序，防止已确认的新修改被旧响应覆盖。
    private fun nextChangeTime(): Long = orderingClock.updateAndGet { maxOf(System.currentTimeMillis(), it + 1) }
    private fun setLoad(query: Query, value: Loading) { loads.update { it + (query to value) } }
    private fun transient(error: Exception) = error is IOException ||
        error is HttpException && (error.code() >= 500 || error.code() in listOf(408, 429)) ||
        error is ApiException && error.code in listOf(-500, -504)
    private fun message(error: Exception) = if (error is IllegalArgumentException) error.message ?: "操作参数不正确" else error.toUserMessage()
    private fun LibraryVideo.entity(account: Long, kind: LibraryKind, folder: Long, time: Long, pending: Boolean) =
        LibraryEntryEntity(account, kind.name, folder, bvid, aid, cid, title, coverUrl, ownerName,
            durationSeconds, progressSeconds, viewedAt, if (viewedAt > 0) viewedAt * 1000 else time, time, pending = pending)
    private fun LibraryEntryEntity.operation(desired: Boolean, completed: Boolean, time: Long) =
        LibraryOutboxEntity(accountId, kind, folderId, bvid, UUID.randomUUID().toString(), desired, aid, cid,
            progressSeconds.coerceAtLeast(0), completed, time)

    companion object {
        const val LIKES_SCOPE = "B站仅提供最近点赞列表；这里合并最近点赞与本机已记录的点赞，不能代表账号的全部点赞历史。"
    }
}
