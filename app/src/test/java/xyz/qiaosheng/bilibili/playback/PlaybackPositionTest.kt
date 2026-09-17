package xyz.qiaosheng.bilibili.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {
    @Test fun unfinishedVideoResumesInMilliseconds() {
        assertEquals(35_000L, resumePositionMillis(35, 120))
    }

    @Test fun completedRemoteSentinelRestartsFromBeginning() {
        assertEquals(0L, resumePositionMillis(-1, 120))
    }

    @Test fun completedOrOutOfRangeProgressDoesNotImmediatelyEndPlayback() {
        assertEquals(0L, resumePositionMillis(120, 120))
        assertEquals(0L, resumePositionMillis(Long.MAX_VALUE, 120))
    }

    @Test fun MissingProgressOrDurationDoesNotSeekOutsideTheVideo() {
        assertEquals(0L, resumePositionMillis(0, 120))
        assertEquals(0L, resumePositionMillis(90, 0))
    }
}
