package xyz.qiaosheng.bilibili.data.repository

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.LoginRequiredException
import xyz.qiaosheng.bilibili.data.local.entity.FavoriteFolderEntity
import xyz.qiaosheng.bilibili.data.remote.RemoteLibraryPage
import xyz.qiaosheng.bilibili.data.remote.dto.FavoriteFolderDto
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.support.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {
    private val video = LibraryVideo("BV-test", 10, 20, "视频", "https://example.test/cover", "作者", 100)

    @Test fun offlineMutationSurvivesFailureAndLaterAcknowledges() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote(); val session = FakeLibrarySession()
        val repository = LibraryRepository(dao, remote, session, FakeLibraryScheduler(), backgroundScope)
        repository.setLiked(video, true, 1)
        remote.onSubmit = { _, _ -> throw IOException("offline") }
        assertTrue(repository.syncPending(1))
        assertTrue(repository.observeVideoStatus(video.bvid).first().liked)
        assertEquals(1, repository.observe(LibraryKind.LIKES).first().pendingCount)
        assertNotNull(dao.outbox(1).single().error)
        remote.onSubmit = { _, _ -> }
        assertFalse(repository.syncPending(1))
        assertTrue(dao.outbox(1).isEmpty())
        assertTrue(repository.observeVideoStatus(video.bvid).first().liked)
    }

    @Test fun oldAcknowledgementCannotDropANewerOppositeIntent() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        repository.setLiked(video, true, 1)
        remote.onSubmit = { _, _ -> entered.complete(Unit); release.await() }
        val syncing = launch { repository.syncPending(1) }
        entered.await()
        repository.setLiked(video.copy(cid = 21), false, 1)
        val newerId = dao.outbox(1).single().mutationId
        release.complete(Unit); syncing.join()
        assertEquals(newerId, dao.outbox(1).single().mutationId)
        assertFalse(dao.outbox(1).single().desired)
        assertFalse(repository.observeVideoStatus(video.bvid).first().liked)
    }

    @Test fun responseFromPreviousAccountNeverEntersCurrentAccountCache() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote(); val session = FakeLibrarySession()
        val repository = LibraryRepository(dao, remote, session, FakeLibraryScheduler(), backgroundScope)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.onHistory = { credentials, _ -> assertEquals(1L, credentials.accountId); entered.complete(Unit); release.await(); RemoteLibraryPage(listOf(video)) }
        val refresh = launch { repository.refresh(LibraryKind.HISTORY) }
        entered.await(); session.accountId.value = 2; release.complete(Unit); refresh.join()
        assertTrue(repository.observe(LibraryKind.HISTORY).first().items.isEmpty())
        assertTrue(dao.entries.value.isEmpty())
    }

    @Test fun visitorHistoryIsNotMigratedAndOldVisitorActionCannotWriteNewAccount() = runTest {
        val dao = FakeLibraryDao(); val session = FakeLibrarySession(null)
        val repository = LibraryRepository(dao, FakeLibraryRemote(), session, FakeLibraryScheduler(), backgroundScope)
        repository.recordWatch(0, video, 35)
        val visitor = repository.observe(LibraryKind.HISTORY).first()
        assertNull(visitor.accountId); assertFalse(visitor.loginRequired); assertEquals(1, visitor.items.size)
        assertTrue(dao.operations.value.isEmpty())
        session.accountId.value = 2
        assertTrue(repository.observe(LibraryKind.HISTORY).first().items.isEmpty())
        try { repository.deleteHistory(video, 0); fail("stale guest event accepted") } catch (_: LoginRequiredException) { }
        assertEquals(0L, dao.entries.value.single().accountId)
    }

    @Test fun resumeIsPerPartAndCompletedPlaybackRestartsAtZero() = runTest {
        val repository = LibraryRepository(FakeLibraryDao(), FakeLibraryRemote(), FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        repository.recordWatch(1, video, 35)
        repository.recordWatch(1, video.copy(cid = 21), 75)
        assertEquals(35L, repository.resumePosition(1, video.bvid, 20))
        assertEquals(75L, repository.resumePosition(1, video.bvid, 21))
        repository.recordWatch(1, video, 100, completed = true)
        assertEquals(0L, repository.resumePosition(1, video.bvid, 20))
        assertEquals(75L, repository.resumePosition(1, video.bvid, 21))
    }

    @Test fun favoriteRefreshOnlyPrunesAbsentCacheAfterACompleteScan() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        dao.putFolders(listOf(FavoriteFolderEntity(1, 10, "收藏", 2)))
        repository.setFavorite(video.copy(bvid = "old"), 10, true, 1); repository.syncPending(1)
        remote.onFavorites = { _, _, cursor -> if (cursor == null) RemoteLibraryPage(listOf(video), "2", true) else RemoteLibraryPage(emptyList()) }
        repository.refresh(LibraryKind.FAVORITES, 10)
        assertEquals(setOf("old", video.bvid), repository.observe(LibraryKind.FAVORITES, 10).first().items.map { it.bvid }.toSet())
        repository.loadMore(LibraryKind.FAVORITES, 10)
        assertEquals(listOf(video.bvid), repository.observe(LibraryKind.FAVORITES, 10).first().items.map { it.bvid })
    }

    @Test fun recentLikeWindowDoesNotDeleteOlderLocallyKnownLikesOrResurrectUnlike() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        repository.setLiked(video.copy(bvid = "older"), true, 1); repository.syncPending(1)
        repository.setLiked(video, false, 1); repository.syncPending(1)
        remote.onRecentLikes = { RemoteLibraryPage(listOf(video, video.copy(bvid = "recent"))) }
        repository.refresh(LibraryKind.LIKES)
        val snapshot = repository.observe(LibraryKind.LIKES).first()
        assertEquals(setOf("older", "recent"), snapshot.items.map { it.bvid }.toSet())
        assertFalse(snapshot.hasMore); assertNotNull(snapshot.remoteScopeNotice)
    }

    @Test fun loginFailureRetainsIntentAndManualRetryAfterLoginCanFinish() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        runCurrent() // 先处理应用启动的队列恢复，再模拟当前会话过期。
        repository.setLiked(video, true, 1)
        remote.onSubmit = { _, _ -> throw LoginRequiredException() }
        assertFalse(repository.syncPending(1))
        assertEquals("LOGIN_REQUIRED", dao.outbox(1).single().state)
        assertTrue(repository.observe(LibraryKind.LIKES).first().loginRequired)
        remote.onSubmit = { _, _ -> }; repository.sync()
        assertTrue(dao.outbox(1).isEmpty())
    }

    @Test fun permanentFailureDoesNotSpinInBackgroundAndWrongAccountWorkerDoesNothing() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote(); val session = FakeLibrarySession()
        val repository = LibraryRepository(dao, remote, session, FakeLibraryScheduler(), backgroundScope)
        repository.setLiked(video, true, 1)
        remote.onSubmit = { _, _ -> throw IllegalArgumentException("失效视频") }
        repository.syncPending(1); repository.syncPending(1)
        assertEquals(1, remote.submissions.size)
        assertEquals("FAILED", dao.outbox(1).single().state)
        session.accountId.value = 2; repository.syncPending(1)
        assertEquals(1, remote.submissions.size)
    }

    @Test fun preciseFavoriteStatusUsesFolderMembershipAndRetainsPendingLocalOverride() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        remote.onFolders = { _, aid -> assertEquals(10L, aid); listOf(FavoriteFolderDto(10, "甲", favState = 0), FavoriteFolderDto(11, "乙", favState = 1)) }
        repository.refreshVideoStatus(video)
        assertEquals(setOf(11L), repository.observeVideoStatus(video.bvid).first().favoriteFolderIds)
        repository.setFavorite(video, 10, true, 1)
        repository.refreshVideoStatus(video.copy(cid = 99))
        assertEquals(setOf(10L, 11L), repository.observeVideoStatus(video.bvid).first().favoriteFolderIds)
        assertEquals(1, dao.outbox(1).size)
    }

    @Test fun deletionHidesHistoryAndClearsResumePositionsEvenWithoutNetwork() = runTest {
        val dao = FakeLibraryDao()
        val repository = LibraryRepository(dao, FakeLibraryRemote(), FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        repository.recordWatch(1, video, 60)
        repository.deleteHistory(video, 1)
        assertTrue(repository.observe(LibraryKind.HISTORY).first().items.isEmpty())
        assertEquals(0L, repository.resumePosition(1, video.bvid, video.cid))
        assertFalse(dao.outbox(1).single().desired)
    }

    @Test fun expiredResponseFromPreviousSessionCannotBlockFreshLoginOfSameAccount() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote(); val session = FakeLibrarySession()
        val repository = LibraryRepository(dao, remote, session, FakeLibraryScheduler(), backgroundScope)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        repository.setLiked(video, true, 1)
        remote.onSubmit = { _, _ -> entered.complete(Unit); release.await(); throw LoginRequiredException() }
        val syncing = async { repository.syncPending(1) }
        entered.await(); session.sessionVersion++; release.complete(Unit)
        assertTrue(syncing.await())
        assertEquals("PENDING", dao.outbox(1).single().state)
        remote.onSubmit = { _, _ -> }
        repository.syncPending(1)
        assertTrue(dao.outbox(1).isEmpty())
    }

    @Test fun staleStatusResponseCannotOverwriteNewMutationEvenAfterServerAcknowledgesIt() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.onLiked = { _, _ -> entered.complete(Unit); release.await(); false }
        val refresh = launch { repository.refreshVideoStatus(video) }
        entered.await()
        repository.setLiked(video, true, 1); repository.syncPending(1)
        release.complete(Unit); refresh.join()
        assertTrue(repository.observeVideoStatus(video.bvid).first().liked)
        assertTrue(dao.outbox(1).isEmpty())
    }

    @Test fun removedRemoteFolderDoesNotRemainInVideoMembership() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        remote.onFolders = { _, _ -> listOf(FavoriteFolderDto(10, "稍后删除", favState = 1)) }
        repository.refreshVideoStatus(video)
        assertEquals(setOf(10L), repository.observeVideoStatus(video.bvid).first().favoriteFolderIds)
        remote.onFolders = { _, _ -> emptyList() }
        repository.refreshVideoStatus(video)
        assertTrue(repository.observeVideoStatus(video.bvid).first().favoriteFolderIds.isEmpty())
    }

    @Test fun likeVerifiedOnAnotherDeviceCanRestorePreviouslyCanceledLocalRow() = runTest {
        val dao = FakeLibraryDao(); val remote = FakeLibraryRemote()
        val repository = LibraryRepository(dao, remote, FakeLibrarySession(), FakeLibraryScheduler(), backgroundScope)
        repository.setLiked(video, false, 1); repository.syncPending(1)
        remote.onRecentLikes = { RemoteLibraryPage(listOf(video)) }
        var verificationCalls = 0
        remote.onLiked = { _, _ -> verificationCalls++; true }
        repository.refresh(LibraryKind.LIKES)
        assertEquals(1, verificationCalls)
        assertEquals(listOf(video.bvid), repository.observe(LibraryKind.LIKES).first().items.map { it.bvid })
    }
}
