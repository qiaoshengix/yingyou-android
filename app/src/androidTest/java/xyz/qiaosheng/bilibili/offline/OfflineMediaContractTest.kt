package xyz.qiaosheng.bilibili.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.qiaosheng.bilibili.model.video.VideoDetail
import xyz.qiaosheng.bilibili.model.video.VideoOwner
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity

@RunWith(AndroidJUnit4::class)
class OfflineMediaContractTest {
    @Test fun mediaSessionPayloadContainsBothCacheKeysAndNoRemotePlaybackUris() {
        val page = VideoPageData(1, 2, VideoDetail("BV-test", "title", "description", "", 10,
            0, 0, 0, 0, 0, 0, 0, 0, VideoOwner(7, "owner", "", 0, false)))
        val id = OfflineKeys.id(42, "BV-test", 2)
        val row = OfflineVideoEntity(id, 42, "BV-test", 2, "{}", 32, "480P", "avc1", 100,
            30280, "mp4a", 90, "https://secret-cdn.test/video?expires=1", "https://secret-cdn.test/audio?expires=1",
            OfflineKeys.cacheKey(id, "video", 32, "avc1", 100),
            OfflineKeys.cacheKey(id, "audio", 30280, "mp4a", 90), 1)
        val item = OfflineMediaContract.mediaItem(page, row)
        assertTrue(OfflineMediaContract.isOffline(item))
        assertEquals("offline", item.requestMetadata.mediaUri?.scheme)
        assertEquals("offline", item.localConfiguration?.uri?.scheme)
        val extras = requireNotNull(item.requestMetadata.extras)
        assertEquals(42L, extras.getLong(OfflineMediaContract.ACCOUNT_ID))
        assertEquals(2L, extras.getLong(OfflineMediaContract.CID))
        assertEquals(row.videoKey, extras.getString(OfflineMediaContract.VIDEO_KEY))
        assertEquals(row.audioKey, extras.getString(OfflineMediaContract.AUDIO_KEY))
        assertFalse(extras.keySet().any { extras.get(it).toString().contains("secret-cdn") })
        // 模拟调用方追加播放历史元数据，原来的离线契约必须保留。
        val copy = item.buildUpon().setRequestMetadata(item.requestMetadata.buildUpon().setExtras(
            android.os.Bundle(extras).apply { putLong("history.accountId", 42) }
        ).build()).build()
        assertTrue(OfflineMediaContract.isOffline(copy))
        assertEquals(row.audioKey, copy.requestMetadata.extras?.getString(OfflineMediaContract.AUDIO_KEY))
    }
}
