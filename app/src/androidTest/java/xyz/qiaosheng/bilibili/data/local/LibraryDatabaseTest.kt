package xyz.qiaosheng.bilibili.data.local

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.local.entity.LibraryEntryEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryOutboxEntity
import xyz.qiaosheng.bilibili.data.local.entity.WatchProgressEntity

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var context: Context
    private lateinit var name: String
    private var database: BiliDatabase? = null

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        name = "library-test-${UUID.randomUUID()}.db"
    }

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun open(): BiliDatabase = Room.databaseBuilder(context, BiliDatabase::class.java, name)
        .addMigrations(BiliDatabase.MIGRATION_1_2).build().also { database = it }

    @Test fun versionOneMigrationKeepsSearchHistoryAndValidatesNewRoomSchema() = runBlocking {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL("CREATE TABLE search_history (keyword TEXT NOT NULL, searchTime INTEGER NOT NULL, PRIMARY KEY(keyword))")
            legacy.execSQL("INSERT INTO search_history VALUES (?, ?)", arrayOf<Any?>("保留原搜索记录", 12345L))
            legacy.version = 1
        }
        val db = open()
        // 首次打开触发 Room 对全部迁移后表结构的校验；测试不使用破坏性回退。
        db.openHelper.writableDatabase.query("SELECT keyword, searchTime FROM search_history").use {
            assertTrue(it.moveToFirst()); assertEquals("保留原搜索记录", it.getString(0)); assertEquals(12345L, it.getLong(1))
        }
        assertEquals(2, db.openHelper.writableDatabase.version)
        assertTrue(db.libraryDao().outbox(1).isEmpty())
        assertNull(db.libraryDao().progress(1, "BV-test", 20))
    }

    @Test fun accountKeysAndOutboxVersionsSurviveDatabaseReopen() = runBlocking {
        var db = open()
        db.libraryDao().mutate(entry(1), operation(1, "old", true))
        db.libraryDao().mutate(entry(2), operation(2, "other-account", true))
        db.libraryDao().mutate(entry(1).copy(deleted = true), operation(1, "latest", false))
        db.close(); db = open()
        db.libraryDao().acknowledge(operation(1, "old", true))
        assertEquals("latest", db.libraryDao().outbox(1).single().mutationId)
        assertEquals("other-account", db.libraryDao().outbox(2).single().mutationId)
        assertTrue(db.libraryDao().observeEntries(1, "LIKES", 0).first().isEmpty())
        assertEquals(1, db.libraryDao().observeEntries(2, "LIKES", 0).first().size)
        db.libraryDao().acknowledge(operation(1, "latest", false))
        assertTrue(db.libraryDao().outbox(1).isEmpty())
        assertFalse(requireNotNull(db.libraryDao().entry(1, "LIKES", 0, "BV-test")).pending)
        assertEquals(1, db.libraryDao().outbox(2).size)
    }

    @Test fun failedOutboxWriteRollsBackOptimisticEntryAndProgress() = runBlocking {
        val db = open()
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_test_outbox BEFORE INSERT ON library_outbox
            BEGIN SELECT RAISE(ABORT, 'test write failure'); END""")
        try {
            db.libraryDao().mutate(entry(1).copy(kind = "HISTORY"), operation(1, "failing", true).copy(kind = "HISTORY"),
                WatchProgressEntity(1, "BV-test", 20, 40, false, 100))
            fail("transaction should fail")
        } catch (_: SQLiteException) { }
        assertNull(db.libraryDao().entry(1, "HISTORY", 0, "BV-test"))
        assertNull(db.libraryDao().progress(1, "BV-test", 20))
        assertTrue(db.libraryDao().outbox(1).isEmpty())
    }

    private fun entry(account: Long) = LibraryEntryEntity(account, "LIKES", 0, "BV-test", 10, 20,
        "视频", "", "作者", 100, 40, 0, 100, 100, pending = true)

    private fun operation(account: Long, id: String, desired: Boolean) =
        LibraryOutboxEntity(account, "LIKES", 0, "BV-test", id, desired, 10, 20, 40, false, 100)
}
