package xyz.qiaosheng.bilibili.ui.library

import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.qiaosheng.bilibili.data.local.entity.FavoriteFolderEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryEntryEntity
import xyz.qiaosheng.bilibili.data.remote.RemoteLibraryPage
import xyz.qiaosheng.bilibili.data.remote.dto.FavoriteFolderDto
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.support.FakeLibraryDao
import xyz.qiaosheng.bilibili.support.FakeLibraryRemote
import xyz.qiaosheng.bilibili.support.FakeLibraryScheduler
import xyz.qiaosheng.bilibili.support.FakeLibrarySession

/** 使用真实仓库和 DAO 合并/队列逻辑，替换磁盘、远程服务和账号来源。 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dao = FakeLibraryDao()
    private val remote = FakeLibraryRemote()
    private val session = FakeLibrarySession()
    private val scheduler = FakeLibraryScheduler()
    private lateinit var vm: LibraryViewModel

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }

    @After fun teardown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.create(kind: LibraryKind = LibraryKind.HISTORY) {
        vm = LibraryViewModel(LibraryRepository(dao, remote, session, scheduler, backgroundScope))
        vm.open(kind)
    }

    @Test fun cachedRowsAppearBeforeRemoteReplyAndRepeatedRowsAreMerged() = runTest {
        val local = entry(1, "BV1cached")
        dao.putEntry(local)
        val reply = CompletableDeferred<RemoteLibraryPage>()
        remote.onHistory = { _, _ -> reply.await() }
        create()
        runCurrent()

        assertFalse(vm.uiState.value.initialLoading)
        assertTrue(vm.uiState.value.snapshot!!.loading)
        assertEquals(listOf("BV1cached"), vm.uiState.value.snapshot!!.items.map { it.bvid })

        val updated = local.video().copy(title = "远端更新的标题")
        reply.complete(RemoteLibraryPage(listOf(updated, updated)))
        runCurrent()
        assertFalse(vm.uiState.value.snapshot!!.loading)
        assertEquals(listOf(updated), vm.uiState.value.snapshot!!.items)
    }

    @Test fun repeatedRefreshClicksShareTheCurrentRequest() = runTest {
        val reply = CompletableDeferred<RemoteLibraryPage>()
        var requests = 0
        remote.onHistory = { _, _ -> requests++; reply.await() }
        create()
        runCurrent()
        repeat(5) { vm.refresh() }
        runCurrent()
        assertEquals(1, requests)

        reply.complete(RemoteLibraryPage(emptyList()))
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(2, requests)
        assertFalse(vm.uiState.value.snapshot!!.loading)
    }

    @Test fun accountSwitchKeepsNewAccountRowsWhenOldNetworkReplyArrives() = runTest {
        dao.putEntry(entry(1, "BV1accountA"))
        dao.putEntry(entry(2, "BV1accountB"))
        val oldReply = CompletableDeferred<RemoteLibraryPage>()
        remote.onHistory = { credentials, _ ->
            if (credentials.accountId == 1L) withContext(NonCancellable) { oldReply.await() }
            else RemoteLibraryPage(emptyList())
        }
        create()
        runCurrent()
        session.accountId.value = 2
        runCurrent()
        assertEquals(2L, vm.uiState.value.snapshot!!.accountId)
        assertEquals(listOf("BV1accountB"), vm.uiState.value.snapshot!!.items.map { it.bvid })

        oldReply.complete(RemoteLibraryPage(listOf(LibraryVideo("BV1lateA"))))
        runCurrent()
        assertEquals(2L, vm.uiState.value.snapshot!!.accountId)
        assertEquals(listOf("BV1accountB"), vm.uiState.value.snapshot!!.items.map { it.bvid })
        assertNull(vm.uiState.value.requestError)
        assertNull(dao.entry(2, "HISTORY", 0, "BV1lateA"))
    }

    @Test fun staleGuestDeleteCannotRemoveTheSameVideoFromTheNewAccount() = runTest {
        session.accountId.value = null
        val guest = entry(0, "BV1shared")
        dao.putEntry(guest)
        dao.putEntry(entry(1, "BV1shared"))
        create()
        runCurrent()
        assertNull(vm.uiState.value.snapshot!!.accountId)

        // 会话先变化，界面的旧快照尚未重组；仓库仍必须校验捕获的访客身份。
        session.accountId.value = 1
        vm.remove(guest.video(), accountId = null)
        runCurrent()
        assertFalse(dao.entry(0, "HISTORY", 0, "BV1shared")!!.deleted)
        assertFalse(dao.entry(1, "HISTORY", 0, "BV1shared")!!.deleted)
        assertTrue(dao.operations.value.isEmpty())
    }

    @Test fun failedAppendWaitsForExplicitRetryAndKeepsCachedRows() = runTest {
        var appendRequests = 0
        var failAppend = true
        remote.onHistory = { _, cursor ->
            if (cursor == null) RemoteLibraryPage(listOf(LibraryVideo("BV1first")), "next", true)
            else {
                appendRequests++
                if (failAppend) throw IOException("offline")
                RemoteLibraryPage(listOf(LibraryVideo("BV1second")))
            }
        }
        create()
        runCurrent()
        repeat(3) { vm.loadMore() }
        runCurrent()
        assertEquals(1, appendRequests)
        assertNotNull(vm.uiState.value.snapshot!!.error)
        assertEquals(listOf("BV1first"), vm.uiState.value.snapshot!!.items.map { it.bvid })

        repeat(10) { vm.loadMore() }
        runCurrent()
        assertEquals(1, appendRequests)
        failAppend = false
        vm.loadMore(retry = true)
        runCurrent()
        assertEquals(2, appendRequests)
        assertEquals(setOf("BV1first", "BV1second"), vm.uiState.value.snapshot!!.items.map { it.bvid }.toSet())
        assertFalse(vm.uiState.value.snapshot!!.hasMore)
        assertNull(vm.uiState.value.snapshot!!.error)
    }

    @Test fun duplicateOfflineRemovalKeepsOnePendingIntentAndDoesNotRestoreTheRow() = runTest {
        val liked = entry(1, "BV1liked", LibraryKind.LIKES)
        dao.putEntry(liked)
        remote.onRecentLikes = { throw IOException("offline") }
        remote.onSubmit = { _, _ -> throw IOException("offline") }
        create(LibraryKind.LIKES)
        runCurrent()
        assertEquals(listOf(liked.video()), vm.uiState.value.snapshot!!.items)
        assertNotNull(vm.uiState.value.snapshot!!.error)
        val priorSchedules = scheduler.accounts.size

        repeat(3) { vm.remove(liked.video(), accountId = 1) }
        runCurrent()
        assertEquals(priorSchedules + 1, scheduler.accounts.size)
        assertTrue(vm.uiState.value.snapshot!!.items.isEmpty())
        assertEquals(1, vm.uiState.value.snapshot!!.pendingCount)
        val pending = dao.operations.value.single()
        assertFalse(pending.desired)

        vm.sync()
        runCurrent()
        assertTrue(vm.uiState.value.snapshot!!.items.isEmpty())
        assertEquals(pending.mutationId, dao.operations.value.single().mutationId)
        assertEquals(1, dao.operations.value.single().attempts)
        assertFalse(vm.uiState.value.operationInProgress)
    }

    @Test fun switchingFoldersShowsNewLocalRowsWhileAnOldFolderRequestFinishes() = runTest {
        dao.putFolders(listOf(FavoriteFolderEntity(1, 10, "A夹", 1), FavoriteFolderEntity(1, 20, "B夹", 1)))
        dao.putEntry(entry(1, "BV1folderA", LibraryKind.FAVORITES, 10))
        val second = entry(1, "BV1folderB", LibraryKind.FAVORITES, 20)
        dao.putEntry(second)
        remote.onFolders = { _, _ -> listOf(FavoriteFolderDto(10, "A夹"), FavoriteFolderDto(20, "B夹")) }
        val firstReply = CompletableDeferred<RemoteLibraryPage>()
        remote.onFavorites = { _, folderId, _ ->
            if (folderId == 10L) withContext(NonCancellable) { firstReply.await() }
            else RemoteLibraryPage(listOf(second.video()))
        }
        create(LibraryKind.FAVORITES)
        runCurrent()
        vm.selectFolder(20)
        runCurrent()
        assertEquals(20L, vm.uiState.value.snapshot!!.selectedFolderId)
        assertEquals(listOf(second.video()), vm.uiState.value.snapshot!!.items)

        firstReply.complete(RemoteLibraryPage(listOf(LibraryVideo("BV1lateFolderA", folderId = 10))))
        runCurrent()
        assertEquals(20L, vm.uiState.value.snapshot!!.selectedFolderId)
        assertEquals(listOf(second.video()), vm.uiState.value.snapshot!!.items)
        assertNull(vm.uiState.value.requestError)
    }

    private fun entry(
        accountId: Long,
        bvid: String,
        kind: LibraryKind = LibraryKind.HISTORY,
        folderId: Long = 0
    ) = LibraryEntryEntity(
        accountId = accountId, kind = kind.name, folderId = folderId, bvid = bvid,
        aid = 100, cid = 200, title = "本地 $bvid", coverUrl = "", ownerName = "UP主",
        durationSeconds = 600, progressSeconds = 60, viewedAt = 1, sortAt = 1_000, modifiedAt = 1
    )
}
