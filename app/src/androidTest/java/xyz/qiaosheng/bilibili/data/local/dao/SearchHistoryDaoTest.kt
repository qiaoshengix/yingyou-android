package xyz.qiaosheng.bilibili.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: BiliDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, BiliDatabase::class.java)
            .addCallback(BiliDatabase.HISTORY_RETENTION_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsDeleteOldRowsAndRepeatedSearchMovesToFront() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        for (time in 1L..25L) {
            dao.insert(SearchHistoryEntity("keyword-$time", time))
            assertEquals(minOf(time.toInt(), 20), storedCount(database))
        }
        assertEquals((25L downTo 6L).map { "keyword-$it" }, dao.getAll().first().map { it.keyword })

        dao.insert(SearchHistoryEntity("keyword-6", 26L))
        assertEquals(20, storedCount(database))
        assertEquals("keyword-6", dao.getAll().first().first().keyword)
        assertEquals(1, dao.getAll().first().count { it.keyword == "keyword-6" })

        dao.insert(SearchHistoryEntity("keyword-27", 27L))
        assertEquals(20, storedCount(database))
        assertEquals(
            listOf("keyword-27", "keyword-6") + (25L downTo 8L).map { "keyword-$it" },
            dao.getAll().first().map { it.keyword }
        )
    }

    @Test
    fun equalTimestampsKeepLatestInsertsAndRepeatedSearches() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        for (index in 1..25) dao.insert(SearchHistoryEntity("keyword-$index", 100L))
        assertEquals(20, storedCount(database))
        assertEquals((25 downTo 6).map { "keyword-$it" }, dao.getAll().first().map { it.keyword })

        dao.insert(SearchHistoryEntity("keyword-6", 100L))
        dao.insert(SearchHistoryEntity("keyword-26", 100L))
        assertEquals(20, storedCount(database))
        assertEquals(
            listOf("keyword-26", "keyword-6") + (25 downTo 8).map { "keyword-$it" },
            dao.getAll().first().map { it.keyword }
        )
    }

    @Test
    fun retentionUsesSearchTimeEvenWhenRecordsArriveOutOfOrder() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        for (time in 30L downTo 1L) dao.insert(SearchHistoryEntity("keyword-$time", time))
        assertEquals(20, storedCount(database))
        assertEquals((30L downTo 11L).map { "keyword-$it" }, dao.getAll().first().map { it.keyword })
    }

    @Test
    fun deletingOneKeywordAndClearingHistoryRemovePersistedRows() = runBlocking(Dispatchers.IO) {
        val dao = database.SearchHistoryDao()
        dao.insert(SearchHistoryEntity("keep", 1L))
        dao.insert(SearchHistoryEntity("remove", 2L))
        dao.delete("remove")
        assertEquals(1, storedCount(database))
        assertEquals(listOf("keep"), dao.getAll().first().map { it.keyword })
        dao.clearAll()
        assertEquals(0, storedCount(database))
    }

    @Test
    fun openingExistingDatabasePrunesOverflowBeforeFirstQueryAndSurvivesReopen() {
        val name = "search-history-retention-${UUID.randomUUID()}.db"
        val expected = (30 downTo 11).map { "legacy-$it" }
        try {
            // Build without the new callback to reproduce an already populated version 1 database.
            val oldDatabase = Room.databaseBuilder(context, BiliDatabase::class.java, name).build()
            try {
                oldDatabase.runInTransaction {
                    for (index in 1..30) {
                        oldDatabase.openHelper.writableDatabase.execSQL(
                            "INSERT INTO search_history (keyword, searchTime) VALUES (?, ?)",
                            arrayOf<Any>("legacy-$index", 100L)
                        )
                    }
                }
                assertEquals(30, storedCount(oldDatabase))
            } finally {
                oldDatabase.close()
            }

            repeat(2) {
                val reopened = Room.databaseBuilder(context, BiliDatabase::class.java, name)
                    .addCallback(BiliDatabase.HISTORY_RETENTION_CALLBACK)
                    .build()
                try {
                    // This unrestricted COUNT cannot be hidden by the DAO's LIMIT 20.
                    assertEquals(20, storedCount(reopened))
                    assertEquals(expected, storedKeywords(reopened))
                } finally {
                    reopened.close()
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun storedCount(database: BiliDatabase): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM search_history").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun storedKeywords(database: BiliDatabase): List<String> =
        database.openHelper.readableDatabase.query(
            "SELECT keyword FROM search_history ORDER BY searchTime DESC, rowid DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
