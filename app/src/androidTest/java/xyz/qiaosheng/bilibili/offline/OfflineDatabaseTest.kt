package xyz.qiaosheng.bilibili.offline

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.qiaosheng.bilibili.offline.data.OfflineDatabase
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity

@RunWith(AndroidJUnit4::class)
class OfflineDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: OfflineDatabase
    private lateinit var name: String

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        name = "offline-test-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, OfflineDatabase::class.java, name).build()
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun queriesAndMutationsCannotCrossAccountBoundary() = runBlocking {
        val guest = row(0, 1)
        val account = row(42, 1)
        database.offlineDao().put(guest)
        database.offlineDao().put(account)
        assertEquals(listOf(guest.id), database.offlineDao().observe(0).first().map { it.id })
        assertEquals(listOf(account.id), database.offlineDao().observe(42).first().map { it.id })
        assertNull(database.offlineDao().get(guest.id, 42))
        database.offlineDao().setPaused(guest.id, 42, true, "PAUSED")
        database.offlineDao().markRemoving(guest.id, 42)
        assertFalse(requireNotNull(database.offlineDao().get(guest.id, 0)).userPaused)
        assertFalse(requireNotNull(database.offlineDao().get(guest.id, 0)).deleting)
    }

    @Test fun partMetadataPausedStateAndRemovalIntentSurviveReopeningDatabase() = runBlocking {
        val firstPart = row(42, 1)
        val secondPart = row(42, 2)
        database.offlineDao().put(firstPart)
        database.offlineDao().put(secondPart)
        database.offlineDao().setPaused(firstPart.id, 42, true, "PAUSED")
        database.offlineDao().markRemoving(secondPart.id, 42)
        database.close()
        database = Room.databaseBuilder(context, OfflineDatabase::class.java, name).build()
        val restored = requireNotNull(database.offlineDao().find(42, "BV-test", 1))
        assertTrue(restored.userPaused)
        assertEquals("PAUSED", restored.status)
        assertEquals(firstPart.pageJson, restored.pageJson)
        assertEquals(firstPart.videoKey, restored.videoKey)
        assertNull(database.offlineDao().find(42, "BV-test", 2))
        assertTrue(requireNotNull(database.offlineDao().get(secondPart.id, 42)).deleting)
    }

    private fun row(owner: Long, cid: Long): OfflineVideoEntity {
        val id = OfflineKeys.id(owner, "BV-test", cid)
        return OfflineVideoEntity(id, owner, "BV-test", cid, "{\"cid\":$cid}", 32, "480P",
            "avc1", 100, 30280, "mp4a", 90, "https://example.test/video", "https://example.test/audio",
            OfflineKeys.cacheKey(id, "video", 32, "avc1", 100),
            OfflineKeys.cacheKey(id, "audio", 30280, "mp4a", 90), cid)
    }
}
