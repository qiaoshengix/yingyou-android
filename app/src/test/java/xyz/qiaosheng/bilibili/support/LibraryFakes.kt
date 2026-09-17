package xyz.qiaosheng.bilibili.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.data.local.dao.LibraryDao
import xyz.qiaosheng.bilibili.data.local.entity.*
import xyz.qiaosheng.bilibili.data.remote.LibraryRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.RemoteLibraryPage
import xyz.qiaosheng.bilibili.data.remote.dto.FavoriteFolderDto
import xyz.qiaosheng.bilibili.data.sync.*
import xyz.qiaosheng.bilibili.model.library.LibraryVideo

/** 只替换存储原语，合并、队列确认和失败处理继续运行生产 DAO 的实现。 */
class FakeLibraryDao : LibraryDao() {
    val entries = MutableStateFlow<List<LibraryEntryEntity>>(emptyList())
    val operations = MutableStateFlow<List<LibraryOutboxEntity>>(emptyList())
    val folderRows = MutableStateFlow<List<FavoriteFolderEntity>>(emptyList())
    val progressRows = mutableListOf<WatchProgressEntity>()
    override fun observeEntries(accountId: Long, kind: String, folderId: Long) = entries.map { rows ->
        rows.filter { it.accountId == accountId && it.kind == kind && it.folderId == folderId && !it.deleted }
            .sortedByDescending { it.sortAt }
    }
    override fun observeVideo(accountId: Long, bvid: String) = entries.map { rows -> rows.filter { it.accountId == accountId && it.bvid == bvid } }
    override fun observeFolders(accountId: Long) = folderRows.map { rows -> rows.filter { it.accountId == accountId }.sortedBy { it.folderId } }
    override fun observeOutbox(accountId: Long) = operations.map { rows -> rows.filter { it.accountId == accountId } }
    override suspend fun entry(accountId: Long, kind: String, folderId: Long, bvid: String) = entries.value.firstOrNull {
        it.accountId == accountId && it.kind == kind && it.folderId == folderId && it.bvid == bvid
    }
    override suspend fun outbox(accountId: Long) = operations.value.filter { it.accountId == accountId }
    override suspend fun folders(accountId: Long) = folderRows.value.filter { it.accountId == accountId }.sortedBy { it.folderId }
    override suspend fun progress(accountId: Long, bvid: String, cid: Long) = progressRows.firstOrNull { it.accountId == accountId && it.bvid == bvid && it.cid == cid }
    override suspend fun putEntry(value: LibraryEntryEntity) {
        entries.value = entries.value.filterNot { it.accountId == value.accountId && it.kind == value.kind && it.folderId == value.folderId && it.bvid == value.bvid } + value
    }
    override suspend fun putOutbox(value: LibraryOutboxEntity) {
        operations.value = operations.value.filterNot { it.accountId == value.accountId && it.kind == value.kind && it.folderId == value.folderId && it.bvid == value.bvid } + value
    }
    override suspend fun putFolders(values: List<FavoriteFolderEntity>) {
        val keys = values.map { it.accountId to it.folderId }.toSet()
        folderRows.value = folderRows.value.filterNot { it.accountId to it.folderId in keys } + values
    }
    override suspend fun putProgress(value: WatchProgressEntity) {
        progressRows.removeAll { it.accountId == value.accountId && it.bvid == value.bvid && it.cid == value.cid }
        progressRows.add(value)
    }
    override suspend fun deleteOutbox(accountId: Long, mutationId: String) { operations.value = operations.value.filterNot { it.accountId == accountId && it.mutationId == mutationId } }
    override suspend fun deleteFolders(accountId: Long) { folderRows.value = folderRows.value.filterNot { it.accountId == accountId } }
    override suspend fun deleteProgress(accountId: Long, bvid: String) { progressRows.removeAll { it.accountId == accountId && it.bvid == bvid } }
    override suspend fun pruneRemoteMissing(accountId: Long, kind: String, folderId: Long, generation: String, startedAt: Long) {
        entries.value = entries.value.filterNot { it.accountId == accountId && it.kind == kind && it.folderId == folderId && !it.pending && !it.deleted && it.generation != generation && it.modifiedAt <= startedAt }
    }
}

class FakeLibrarySession(account: Long? = 1L) : LibrarySession {
    override val accountId = MutableStateFlow(account)
    var sessionVersion = 0L
    override fun isCurrent(accountId: Long) = (this.accountId.value ?: 0L) == accountId
    override fun isCurrent(credentials: LibraryCredentials) =
        isCurrent(credentials.accountId) && credentials.sessionVersion == sessionVersion
    override suspend fun initialize() = Unit
    override suspend fun credentials(accountId: Long): LibraryCredentials {
        if (!isCurrent(accountId) || accountId <= 0) throw LoginRequiredException()
        return LibraryCredentials(accountId, "DedeUserID=$accountId; SESSDATA=test-$accountId", "csrf-$accountId", sessionVersion)
    }
}

class FakeLibraryScheduler : LibrarySyncScheduler {
    val accounts = mutableListOf<Long>()
    override fun enqueue(accountId: Long) { accounts.add(accountId) }
}

class FakeLibraryRemote : LibraryRemoteDataSource {
    var onHistory: suspend (LibraryCredentials, String?) -> RemoteLibraryPage = { _, _ -> RemoteLibraryPage(emptyList()) }
    var onFolders: suspend (LibraryCredentials, Long?) -> List<FavoriteFolderDto> = { _, _ -> listOf(FavoriteFolderDto(10, "默认收藏夹")) }
    var onFavorites: suspend (LibraryCredentials, Long, String?) -> RemoteLibraryPage = { _, _, _ -> RemoteLibraryPage(emptyList()) }
    var onRecentLikes: suspend (LibraryCredentials) -> RemoteLibraryPage = { RemoteLibraryPage(emptyList()) }
    var onLiked: suspend (LibraryCredentials, String) -> Boolean = { _, _ -> false }
    var onResolve: suspend (LibraryCredentials, LibraryVideo) -> LibraryVideo = { _, video -> video.copy(aid = video.aid.takeIf { it > 0 } ?: 100, cid = video.cid.takeIf { it > 0 } ?: 200) }
    var onSubmit: suspend (LibraryCredentials, LibraryOutboxEntity) -> Unit = { _, _ -> }
    val submissions = mutableListOf<Pair<LibraryCredentials, LibraryOutboxEntity>>()
    override suspend fun history(session: LibraryCredentials, cursor: String?) = onHistory(session, cursor)
    override suspend fun folders(session: LibraryCredentials, aid: Long?) = onFolders(session, aid)
    override suspend fun favorites(session: LibraryCredentials, folderId: Long, cursor: String?) = onFavorites(session, folderId, cursor)
    override suspend fun recentLikes(session: LibraryCredentials) = onRecentLikes(session)
    override suspend fun liked(session: LibraryCredentials, bvid: String) = onLiked(session, bvid)
    override suspend fun resolve(session: LibraryCredentials, video: LibraryVideo) = onResolve(session, video)
    override suspend fun submit(session: LibraryCredentials, operation: LibraryOutboxEntity) {
        submissions.add(session to operation)
        onSubmit(session, operation)
    }
}
