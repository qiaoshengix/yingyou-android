package xyz.qiaosheng.bilibili.offline

import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineDownloadManagerTest {
    @Test fun twoTrackQueueRemainsPausedAcrossRestartThenResumesAndRemovesBothTracks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val unique = UUID.randomUUID().toString().replace("-", "")
        val directory = File(context.cacheDir, "offline-manager-test-$unique")
        val cache = OfflineCache(context, directory)
        val executor = Executors.newFixedThreadPool(2)
        val index = DefaultDownloadIndex(cache.databaseProvider, "test_$unique")
        val createdSources = AtomicInteger()
        val upstream = DataSource.Factory {
            createdSources.incrementAndGet()
            ByteArrayDataSource(ByteArray(32_768) { (it % 251).toByte() })
        }
        val id = OfflineKeys.id(0, "BV-test", 1)
        val keys = listOf(OfflineKeys.cacheKey(id, "video", 32, "avc1", 100),
            OfflineKeys.cacheKey(id, "audio", 30280, "mp4a", 90))
        val requests = keys.mapIndexed { position, key ->
            DownloadRequest.Builder("$id:$position", "offline://test/$position".toUri())
                .setMimeType(if (position == 0) MimeTypes.VIDEO_MP4 else MimeTypes.AUDIO_MP4)
                .setCustomCacheKey(key).build()
        }
        var manager: DownloadManager? = null
        fun start(listener: DownloadManager.Listener): DownloadManager {
            lateinit var started: DownloadManager
            instrumentation.runOnMainSync {
                started = DownloadManager(context, index, DefaultDownloaderFactory(cache.downloadFactory(upstream), executor))
                started.addListener(listener)
                started.requirements = Requirements(0)
            }
            manager = started
            return started
        }
        try {
            val paused = CountDownLatch(2)
            val first = start(object : DownloadManager.Listener {
                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    if (download.state == Download.STATE_STOPPED) paused.countDown()
                }
            })
            instrumentation.runOnMainSync {
                requests.forEach { first.addDownload(it, OfflineDownloadEngine.STOP_USER) }
                first.resumeDownloads()
            }
            assertTrue("Both tracks should persist in stopped state", paused.await(10, TimeUnit.SECONDS))
            assertEquals(0, createdSources.get())
            instrumentation.runOnMainSync { first.release() }
            manager = null

            val initialized = CountDownLatch(1)
            val completed = CountDownLatch(2)
            val removed = CountDownLatch(2)
            val second = start(object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) { initialized.countDown() }
                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    if (download.state == Download.STATE_COMPLETED) completed.countDown()
                }
                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) { removed.countDown() }
            })
            assertTrue(initialized.await(10, TimeUnit.SECONDS))
            requests.forEach { request ->
                val restored = requireNotNull(index.getDownload(request.id))
                assertEquals(Download.STATE_STOPPED, restored.state)
                assertEquals(OfflineDownloadEngine.STOP_USER, restored.stopReason)
            }
            assertEquals(0, createdSources.get())
            instrumentation.runOnMainSync {
                second.setStopReason(null, Download.STOP_REASON_NONE)
                second.resumeDownloads()
            }
            assertTrue("Video and audio must both finish", completed.await(15, TimeUnit.SECONDS))
            keys.forEach { assertTrue(cache.isComplete(it)) }
            assertTrue(createdSources.get() >= 2)
            instrumentation.runOnMainSync { requests.forEach { second.removeDownload(it.id) } }
            assertTrue(removed.await(10, TimeUnit.SECONDS))
            keys.forEach { assertFalse(cache.isComplete(it)) }
        } finally {
            instrumentation.runOnMainSync { manager?.release() }
            executor.shutdownNow()
            cache.cache.release()
            SimpleCache.delete(directory, cache.databaseProvider)
            cache.databaseProvider.close()
        }
    }
}
