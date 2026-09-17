package xyz.qiaosheng.bilibili.provider

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.ContentObserver
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity

@RunWith(AndroidJUnit4::class)
class SearchHistoryProviderTest {
    private lateinit var database: BiliDatabase
    private lateinit var provider: SearchHistoryProvider
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uri = SearchHistoryProvider.CONTENT_URI

    @Before
    fun setUp() {
        // 独立内存数据库，避免测试清空设备上的真实搜索历史。
        database = Room.inMemoryDatabaseBuilder(context, BiliDatabase::class.java)
            .addCallback(BiliDatabase.HISTORY_RETENTION_CALLBACK)
            .build()
        provider = SearchHistoryProvider().apply {
            attachInfo(TestApplication(this@SearchHistoryProviderTest.context, database), ProviderInfo().apply {
                authority = SearchHistoryProvider.AUTHORITY
            })
        }
    }

    @After
    fun tearDown() {
        provider.shutdown()
        database.close()
    }

    @Test
    fun insertRoundTripsEncodedKeywordAndReplacesExistingRow() {
        val keyword = "中文 / Kotlin?'#%"
        val item = provider.insert(uri, values(keyword, 10L))
        assertEquals(keyword, item.lastPathSegment)
        assertEquals(2, item.pathSegments.size)
        assertEquals(SearchHistoryProvider.MIME_DIR, provider.getType(uri))
        assertEquals(SearchHistoryProvider.MIME_ITEM, provider.getType(item))
        provider.insert(uri, values(keyword, 20L))
        assertEquals(listOf(keyword to 20L), rows(item))
        assertEquals(1, rows(uri).size)
    }

    @Test
    fun providerInsertsPhysicallyKeepOnlyTheLatestTwentyRows() {
        for (time in 1L..25L) {
            provider.insert(uri, values("keyword-$time", time))
            assertEquals(minOf(time.toInt(), 20), storedCount())
        }
        assertEquals((25L downTo 6L).map { "keyword-$it" }, rows(uri).map { it.first })

        provider.insert(uri, values("keyword-6", 26L))
        provider.insert(uri, values("keyword-27", 27L))
        assertEquals(20, storedCount())
        assertEquals(
            listOf("keyword-27", "keyword-6") + (25L downTo 8L).map { "keyword-$it" },
            rows(uri).map { it.first }
        )
    }

    @Test
    fun daoAndProviderShareInsertionOrderForEqualTimestamps() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        for (index in 1..25) {
            if (index % 2 == 0) {
                dao.insert(SearchHistoryEntity("keyword-$index", 100L))
            } else {
                provider.insert(uri, values("keyword-$index", 100L))
            }
        }
        provider.insert(uri, values("keyword-6", 100L))
        dao.insert(SearchHistoryEntity("keyword-7", 100L))
        val expected = listOf("keyword-7", "keyword-6") + (25 downTo 8).map { "keyword-$it" }
        assertEquals(20, storedCount())
        assertEquals(expected, rows(uri).map { it.first })
        assertEquals(expected, dao.getAll().first().map { it.keyword })
    }

    @Test
    fun concurrentDaoAndProviderWritesCannotLeaveOverflowRows() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        val jobs = (1L..60L).map { time ->
            launch {
                if (time % 2L == 0L) {
                    dao.insert(SearchHistoryEntity("keyword-$time", time))
                } else {
                    provider.insert(uri, values("keyword-$time", time))
                }
                assertTrue(storedCount() <= 20)
            }
        }
        jobs.forEach { it.join() }
        assertEquals(20, storedCount())
        assertEquals((60L downTo 41L).map { "keyword-$it" }, rows(uri).map { it.first })
    }

    @Test
    fun querySupportsProjectionSelectionAndSortOrder() {
        provider.insert(uri, values("old", 10L))
        provider.insert(uri, values("new", 30L))
        provider.insert(uri, values("middle", 20L))
        assertEquals(listOf("new", "middle", "old"), rows(uri).map { it.first })
        provider.query(uri, arrayOf("keyword"), "searchTime >= ?", arrayOf("20"), "searchTime ASC").use {
            assertArrayEquals(arrayOf("keyword"), it.columnNames)
            assertEquals(uri, it.notificationUri)
            assertTrue(it.moveToFirst())
            assertEquals("middle", it.getString(0))
            assertTrue(it.moveToNext())
            assertEquals("new", it.getString(0))
            assertFalse(it.moveToNext())
        }
    }

    @Test
    fun itemConditionsCannotUpdateOrDeleteOtherKeywords() {
        val first = provider.insert(uri, values("first", 10L))
        val second = provider.insert(uri, values("second", 20L))
        val update = ContentValues().apply { put("searchTime", 30L) }
        val condition = "keyword = ? OR keyword = ?"
        val args = arrayOf("first", "second")
        assertEquals(1, provider.update(first, update, condition, args))
        assertEquals(listOf("second" to 20L), rows(second))
        assertEquals(0, provider.delete(first, "searchTime = ?", arrayOf("10")))
        assertEquals(1, provider.delete(first, condition, args))
        assertTrue(rows(first).isEmpty())
        assertEquals(0, provider.delete(first, null, null))
        assertEquals(1, rows(uri).size)
    }

    @Test
    fun collectionUpdateDeleteAndDefaultTimestampWork() {
        val input = ContentValues().apply { put("keyword", "automatic") }
        val before = System.currentTimeMillis()
        val item = provider.insert(uri, input)
        assertTrue(rows(item).single().second in before..System.currentTimeMillis())
        assertFalse(input.containsKey("searchTime"))
        provider.insert(uri, values("other", 10L))
        assertEquals(1, provider.update(uri, ContentValues().apply { put("keyword", "renamed") },
            "keyword = ?", arrayOf("other")))
        assertEquals(0, provider.update(uri, ContentValues(), null, null))
        assertEquals(1, provider.delete(uri, "keyword = ?", arrayOf("renamed")))
        assertEquals(1, provider.delete(uri, null, null))
        assertTrue(rows(uri).isEmpty())
    }

    @Test
    fun rejectsUnknownUrisAndInvalidInput() {
        val unknown = uri.buildUpon().appendPath("one").appendPath("two").build()
        assertThrows(IllegalArgumentException::class.java) { provider.getType(unknown) }
        assertThrows(IllegalArgumentException::class.java) { rows(unknown) }
        assertThrows(IllegalArgumentException::class.java) { provider.delete(unknown, null, null) }
        assertThrows(IllegalArgumentException::class.java) {
            provider.update(unknown, ContentValues(), null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.insert(uri.buildUpon().appendPath("one").build(), values("one", 1L))
        }
        assertThrows(IllegalArgumentException::class.java) { provider.insert(uri, null) }
        assertThrows(IllegalArgumentException::class.java) { provider.insert(uri, values("  ", 1L)) }
        assertThrows(IllegalArgumentException::class.java) {
            provider.insert(uri, values("one", 1L).apply { putNull("searchTime") })
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.query(uri, arrayOf("unknown"), null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.query(uri, null, null, null, "searchTime; DELETE FROM search_history")
        }
        assertTrue(rows(uri).isEmpty())
    }

    @Test
    fun writesNotifyContentObserversAndExistingRoomFlow() = runBlocking(Dispatchers.IO) {
        val snapshots = Channel<List<SearchHistoryEntity>>(Channel.UNLIMITED)
        val collection = launch { database.SearchHistoryDao().getAll().collect { snapshots.send(it) } }
        val latch = CountDownLatch(1)
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { latch.countDown() }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        try {
            withTimeout(5_000) { assertTrue(snapshots.receive().isEmpty()) }
            val item = provider.insert(uri, values("observed", 10L))
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            withTimeout(5_000) {
                assertEquals(listOf(SearchHistoryEntity("observed", 10L)), snapshots.receive())
            }
            provider.update(item, ContentValues().apply { put("searchTime", 20L) }, null, null)
            withTimeout(5_000) {
                assertEquals(listOf(SearchHistoryEntity("observed", 20L)), snapshots.receive())
            }
            provider.delete(item, null, null)
            withTimeout(5_000) { assertTrue(snapshots.receive().isEmpty()) }
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
            collection.cancel()
        }
    }

    private fun values(keyword: String, time: Long) = ContentValues().apply {
        put("keyword", keyword)
        put("searchTime", time)
    }

    private fun storedCount(): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM search_history").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun rows(uri: Uri): List<Pair<String, Long>> =
        provider.query(uri, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("keyword")) to
                        cursor.getLong(cursor.getColumnIndexOrThrow("searchTime")))
                }
            }
        }

    private class TestApplication(context: Context, database: BiliDatabase) : Application(),
        GeneratedComponentManager<SearchHistoryProvider.DatabaseEntryPoint> {
        private val entryPoint = object : SearchHistoryProvider.DatabaseEntryPoint, GeneratedComponent {
            override fun database() = database
        }

        init { attachBaseContext(context) }

        override fun getApplicationContext(): Context = this
        override fun generatedComponent(): SearchHistoryProvider.DatabaseEntryPoint = entryPoint
    }
}
