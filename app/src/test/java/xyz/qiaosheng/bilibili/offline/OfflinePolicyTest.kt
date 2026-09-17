package xyz.qiaosheng.bilibili.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.qiaosheng.bilibili.data.remote.dto.Dash
import xyz.qiaosheng.bilibili.data.remote.dto.DashStream
import xyz.qiaosheng.bilibili.data.remote.dto.PlayData
import xyz.qiaosheng.bilibili.model.auth.AuthState
import xyz.qiaosheng.bilibili.offline.data.OfflineVideoEntity

class OfflinePolicyTest {
    @Test fun completionRequiresKnownPositiveLengthAndEveryByte() {
        val spans = listOf(CachedByteSpan(0, 5, true), CachedByteSpan(5, 7, true))
        assertTrue(hasCompleteByteCoverage(12, spans.reversed()))
        assertFalse(hasCompleteByteCoverage(-1, spans))
        assertFalse(hasCompleteByteCoverage(0, spans))
        assertFalse(hasCompleteByteCoverage(13, spans))
        assertFalse(hasCompleteByteCoverage(12, listOf(CachedByteSpan(0, 5, true), CachedByteSpan(6, 6, true))))
    }

    @Test fun missingOrTruncatedCacheFileCannotCountAsComplete() {
        assertFalse(hasCompleteByteCoverage(10, listOf(CachedByteSpan(0, 10, false))))
        assertFalse(hasCompleteByteCoverage(10, listOf(CachedByteSpan(0, 5, true), CachedByteSpan(5, 5, false))))
    }

    @Test fun cacheIdentityIncludesAccountPartAndRepresentationButNotExpiringUrl() {
        val guest = OfflineKeys.id(0, "BV-test", 10)
        val account = OfflineKeys.id(123, "BV-test", 10)
        val otherPart = OfflineKeys.id(0, "BV-test", 11)
        assertNotEquals(guest, account)
        assertNotEquals(guest, otherPart)
        assertEquals(123L, OfflineKeys.accountId(OfflineKeys.cacheKey(account, "video", 32, "avc1", 100)))
        assertNotEquals(OfflineKeys.cacheKey(guest, "video", 32, "avc1", 100),
            OfflineKeys.cacheKey(guest, "video", 32, "hev1", 100))
        assertNotEquals(OfflineKeys.cacheKey(guest, "video", 32, "avc1", 100),
            OfflineKeys.cacheKey(guest, "audio", 32, "avc1", 100))
    }

    @Test fun initializingAccountCannotReadGuestCache() {
        assertEquals(null, AuthState.Initializing.offlineAccountId())
        assertEquals(0L, AuthState.LoggedOut.offlineAccountId())
        assertEquals(42L, AuthState.LoggedIn(42L).offlineAccountId())
    }

    @Test fun selectsReturnedQualityAndCompatibleVideoAndAudio() {
        val avc = stream(32, "avc1", 100)
        val hevc = stream(32, "hev1", 300)
        val aac = stream(30280, "mp4a", 90)
        val data = PlayData(Dash(listOf(hevc, avc, stream(16, "avc1", 50)), listOf(aac, stream(30250, "other", 100))))
        val selected = selectOfflineStreams(data, 32)
        assertEquals(avc, selected.video)
        assertEquals(aac, selected.audio)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unavailableQualityIsNotSilentlyReplacedByAnotherQuality() {
        selectOfflineStreams(PlayData(Dash(listOf(stream(32, "avc1", 100)), listOf(stream(30280, "mp4a", 90)))), 80)
    }

    @Test(expected = IllegalArgumentException::class)
    fun videoWithoutAudioCannotBeEnqueuedAsAnOfflineVideo() {
        selectOfflineStreams(PlayData(Dash(listOf(stream(32, "avc1", 100)), emptyList())), 32)
    }

    @Test fun retryRefreshesSignedUrlsWithoutChangingTheAlreadyCachedRepresentations() {
        val refreshedVideo = stream(32, "avc1", 100).copy(baseUrl = "https://example.test/new-video?expires=2")
        val refreshedAudio = stream(30280, "mp4a", 90).copy(baseUrl = "https://example.test/new-audio?expires=2")
        val selected = selectOfflineStreams(PlayData(Dash(
            listOf(stream(32, "hev1", 300), refreshedVideo),
            listOf(stream(30280, "mp4a", 200), refreshedAudio)
        )), 32, previous())
        assertEquals(refreshedVideo, selected.video)
        assertEquals(refreshedAudio, selected.audio)
    }

    @Test(expected = IllegalArgumentException::class)
    fun retryRejectsAChangedRepresentationInsteadOfMixingItWithPartialBytes() {
        selectOfflineStreams(PlayData(Dash(listOf(stream(32, "avc1", 101)),
            listOf(stream(30280, "mp4a", 90)))), 32, previous())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonNetworkUrisAreNotAcceptedAsDownloadStreams() {
        selectOfflineStreams(PlayData(Dash(listOf(stream(32, "avc1", 100).copy(baseUrl = "file:///private")),
            listOf(stream(30280, "mp4a", 90)))), 32)
    }

    private fun previous() = OfflineVideoEntity("offline:0:BV-test:1", 0, "BV-test", 1, "{}", 32,
        "480P", "avc1", 100, 30280, "mp4a", 90, "https://example.test/old-video?expires=1",
        "https://example.test/old-audio?expires=1", "video-key", "audio-key", 1)

    private fun stream(id: Int, codec: String, bandwidth: Int) =
        DashStream(id, "https://example.test/$id", bandwidth, codec)
}
