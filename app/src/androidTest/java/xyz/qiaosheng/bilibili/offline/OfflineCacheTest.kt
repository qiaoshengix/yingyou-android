package xyz.qiaosheng.bilibili.offline

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineCacheTest {
    private lateinit var cache: OfflineCache
    private lateinit var directory: File
    private val videoKey = OfflineKeys.cacheKey(OfflineKeys.id(0, "BV-test", 1), "video", 32, "avc1", 100)
    private val audioKey = OfflineKeys.cacheKey(OfflineKeys.id(0, "BV-test", 1), "audio", 30280, "mp4a", 90)

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "offline-cache-test-${UUID.randomUUID()}")
        cache = OfflineCache(context, directory)
    }

    @After fun cleanup() {
        cache.cache.release()
        SimpleCache.delete(directory, cache.databaseProvider)
        cache.databaseProvider.close()
    }

    @Test fun bothTracksAreRequiredAndCachedBytesPlayWithoutAnyUpstream() {
        val video = ByteArray(16_384) { (it % 251).toByte() }
        val audio = ByteArray(4_096) { (it % 127).toByte() }
        write(videoKey, video)
        assertTrue(cache.isComplete(videoKey))
        assertFalse(cache.isComplete(audioKey))
        write(audioKey, audio)
        assertTrue(cache.isComplete(videoKey) && cache.isComplete(audioKey))
        assertArrayEquals(video, readLocal(videoKey))
        assertArrayEquals(audio, readLocal(audioKey))
    }

    @Test fun missingChunkFailsClosedInsteadOfFetchingRemoteBytes() {
        write(videoKey, ByteArray(1024) { 7 })
        cache.cache.removeSpan(cache.cache.getCachedSpans(videoKey).first())
        assertFalse(cache.isComplete(videoKey))
        try { readLocal(videoKey); fail("Cache miss must fail without upstream") }
        catch (_: IOException) { }
    }

    @Test fun truncatedFileFailsCompletionEvenIfIndexStillContainsTheSpan() {
        write(videoKey, ByteArray(2048) { 4 })
        val span = cache.cache.getCachedSpans(videoKey).first()
        RandomAccessFile(requireNotNull(span.file), "rw").use { it.setLength(100) }
        assertFalse(cache.isComplete(videoKey))
    }

    @Test fun changingAccountStopsAnAlreadyOpenCachedStream() {
        write(videoKey, ByteArray(2048) { 4 })
        var owner: Long? = 0L
        val source = AccountGuardedDataSource(cache.localOnlyFactory().createDataSource()) { owner }
        source.open(spec(videoKey))
        owner = 42L
        try { source.read(ByteArray(10), 0, 10); fail("Old account stream must stop") }
        catch (_: IOException) { }
        finally { source.close() }
        val second = AccountGuardedDataSource(cache.localOnlyFactory().createDataSource()) { 42L }
        try { second.open(spec(videoKey)); fail("Another account cannot open this cache") }
        catch (_: IOException) { }
        finally { second.close() }
    }

    private fun write(key: String, bytes: ByteArray) {
        val upstream = DataSource.Factory { ByteArrayDataSource(bytes) }
        CacheWriter(cache.downloadFactory(upstream).createDataSourceForDownloading(), spec(key), null, null).cache()
    }

    private fun readLocal(key: String): ByteArray {
        val source = cache.localOnlyFactory().createDataSource()
        val result = ByteArrayOutputStream()
        try {
            source.open(spec(key))
            val buffer = ByteArray(257)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count < 0) break
                result.write(buffer, 0, count)
            }
        } finally { source.close() }
        return result.toByteArray()
    }

    private fun spec(key: String) = DataSpec.Builder().setUri("offline://test/data".toUri()).setKey(key).build()
}
