package xyz.qiaosheng.bilibili.data.repository

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import xyz.qiaosheng.bilibili.data.remote.DanmakuRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionKey
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionSnapshot
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionSource
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

class DanmakuRepositoryTest {
    @Test fun cachesOnlySuccessfulSegmentsWithinTheCurrentSession() = runTest {
        val sessions = FakeSession()
        var requests = 0
        val repository = repository(sessions) { requests++; listOf(item("$requests")) }
        assertEquals("1", repository.loadSegment(1, 10, 1).single().id)
        assertEquals("1", repository.loadSegment(1, 10, 1).single().id)
        assertEquals(1, requests)
        sessions.key = sessions.key.copy(version = 2)
        assertEquals("2", repository.loadSegment(1, 10, 1).single().id)
        sessions.key = DanmakuSessionKey(2, 3)
        assertEquals("3", repository.loadSegment(1, 10, 1).single().id)
        sessions.key = DanmakuSessionKey(null, 4)
        assertEquals("4", repository.loadSegment(1, 10, 1).single().id)
    }

    @Test fun lateResponseAfterSameAccountReloginIsCancelledAndNotCached() = runTest {
        val sessions = FakeSession()
        var requests = 0
        val repository = repository(sessions) {
            requests++
            if (requests == 1) sessions.key = sessions.key.copy(version = 2)
            listOf(item("$requests"))
        }
        assertTrue(runCatching { repository.loadSegment(1, 10, 1) }.exceptionOrNull() is CancellationException)
        assertEquals("2", repository.loadSegment(1, 10, 1).single().id)
        assertEquals(2, requests)
    }

    @Test fun failuresAndCancellationAreNotCachedAsEmptySegments() = runTest {
        val sessions = FakeSession()
        var requests = 0
        val repository = repository(sessions) {
            when (++requests) {
                1 -> throw IOException("offline")
                2 -> throw CancellationException("cancelled")
                else -> emptyList()
            }
        }
        assertTrue(runCatching { repository.loadSegment(1, 10, 1) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { repository.loadSegment(1, 10, 1) }.exceptionOrNull() is CancellationException)
        assertTrue(repository.loadSegment(1, 10, 1).isEmpty())
        assertTrue(repository.loadSegment(1, 10, 1).isEmpty())
        assertEquals(3, requests)
    }

    @Test fun invalidIdentifiersNeverReachNetwork() = runTest {
        var requests = 0
        val repository = repository(FakeSession()) { requests++; emptyList() }
        for ((aid, cid, index) in listOf(Triple(0L, 1L, 1), Triple(1L, 0L, 1), Triple(1L, 1L, 0))) {
            assertTrue(runCatching { repository.loadSegment(aid, cid, index) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, requests)
    }

    @Test fun cacheEvictsLeastRecentlyUsedAndRespectsTotalItemBudget() {
        val cache = DanmakuSegmentCache(maxSegments = 2, maxItems = 3)
        val first = DanmakuSegmentKey(1, 10, 1)
        val second = first.copy(index = 2)
        val third = first.copy(index = 3)
        cache.put(first, listOf(item("1")))
        cache.put(second, listOf(item("2")))
        assertNotNull(cache[first])
        cache.put(third, listOf(item("3"), item("4")))
        assertNull(cache[second])
        assertNotNull(cache[first])
        assertEquals(2, cache[third]?.size)
        cache.put(second, listOf(item("5"), item("6")))
        assertNull(cache[first])
        assertNull(cache[third])
    }

    @Test fun cacheExpiresAndSeparatesCidAidAndSessionVersion() {
        var now = 0L
        val cache = DanmakuSegmentCache(ttlNanos = 10, nowNanos = { now })
        val session = DanmakuSessionKey(1, 1)
        val key = DanmakuSegmentKey(1, 10, 1)
        cache.changeSession(session)
        cache.put(key, listOf(item("1")))
        assertNull(cache[key.copy(cid = 11)])
        assertNull(cache[key.copy(aid = 2)])
        now = 10
        assertNull(cache[key])
        cache.put(key, listOf(item("2")))
        cache.changeSession(session.copy(version = 2))
        assertNull(cache[key])
    }

    private fun repository(sessions: FakeSession, load: suspend () -> List<DanmakuItem>) =
        DanmakuRepository(object : DanmakuRemoteDataSource {
            override suspend fun loadSegment(session: DanmakuSessionSnapshot, aid: Long, cid: Long, segmentIndex: Int) = load()
        }, sessions)

    private class FakeSession : DanmakuSessionSource {
        var key = DanmakuSessionKey(1, 1)
        override suspend fun snapshot() = DanmakuSessionSnapshot(key, "redacted")
        override fun isCurrent(key: DanmakuSessionKey) = this.key == key
    }

    private fun item(id: String) = DanmakuItem(id, 0, "text", 1, 25, 0xFFFFFF)
}
