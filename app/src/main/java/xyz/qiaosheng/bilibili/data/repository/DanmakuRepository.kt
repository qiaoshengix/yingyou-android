package xyz.qiaosheng.bilibili.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.qiaosheng.bilibili.data.remote.DanmakuRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionKey
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionSource
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

@Singleton
class DanmakuRepository @Inject constructor(
    private val remote: DanmakuRemoteDataSource,
    private val sessions: DanmakuSessionSource,
) {
    private val mutex = Mutex()
    private val cache = DanmakuSegmentCache()

    suspend fun loadSegment(aid: Long, cid: Long, segmentIndex: Int): List<DanmakuItem> {
        require(aid > 0 && cid > 0 && segmentIndex > 0)
        currentCoroutineContext().ensureActive()
        val session = sessions.snapshot()
        val key = DanmakuSegmentKey(aid, cid, segmentIndex)
        mutex.withLock {
            requireCurrent(session.key)
            cache.changeSession(session.key)
            cache[key]?.let { return it }
        }
        val items = try {
            remote.loadSegment(session, aid, cid, segmentIndex)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            requireCurrent(session.key)
            throw error
        }
        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            // A response that finishes after logout/re-login never enters any session's cache.
            requireCurrent(session.key)
            cache.changeSession(session.key)
            cache.put(key, items)
            items
        }
    }

    private fun requireCurrent(key: DanmakuSessionKey) {
        if (!sessions.isCurrent(key)) throw CancellationException("Danmaku account changed")
    }
}

internal data class DanmakuSegmentKey(val aid: Long, val cid: Long, val index: Int)

/** Accessed under repository mutex; only a small working set is retained. */
internal class DanmakuSegmentCache(
    private val maxSegments: Int = 6,
    private val maxItems: Int = 12_000,
    private val ttlNanos: Long = 5 * 60 * 1_000_000_000L,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private data class Entry(val items: List<DanmakuItem>, val createdAt: Long)
    private val values = LinkedHashMap<DanmakuSegmentKey, Entry>(8, 0.75f, true)
    private var session: DanmakuSessionKey? = null
    private var itemCount = 0

    init { require(maxSegments > 0 && maxItems > 0 && ttlNanos > 0) }

    fun changeSession(key: DanmakuSessionKey) {
        if (key != session) {
            values.clear()
            itemCount = 0
            session = key
        }
    }

    operator fun get(key: DanmakuSegmentKey): List<DanmakuItem>? {
        val entry = values[key] ?: return null
        if (nowNanos() - entry.createdAt >= ttlNanos) {
            values.remove(key)
            itemCount -= entry.items.size
            return null
        }
        return entry.items
    }

    fun put(key: DanmakuSegmentKey, items: List<DanmakuItem>) {
        values.remove(key)?.let { itemCount -= it.items.size }
        if (items.size > maxItems) return
        values[key] = Entry(items.toList(), nowNanos())
        itemCount += items.size
        while (values.size > maxSegments || itemCount > maxItems) {
            val iterator = values.entries.iterator()
            itemCount -= iterator.next().value.items.size
            iterator.remove()
        }
    }
}
