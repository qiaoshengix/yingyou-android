package xyz.qiaosheng.bilibili.ui.video.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

class DanmakuLayoutEngineTest {
    private val viewport = DanmakuViewport(500f, 400f, 1f)
    private val oneScrollLane = DanmakuViewport(500f, 119f, 1f)

    @Test fun stationaryMediaClockFreezesAndAdvanceUsesOnlyMediaTime() {
        val engine = engine(listOf(item("first", 0)))
        val paused = engine.frame(1_000, viewport)
        repeat(20) { assertEquals(paused, engine.frame(1_000, viewport)) }
        val advanced = engine.frame(3_000, viewport).single()
        // Width is 100px: 600px total travel over 8s, regardless of playback speed.
        assertEquals(275f, advanced.x, 0.001f)
    }

    @Test fun backwardAndForwardSeeksRebuildTheRelevantWindow() {
        val engine = engine(listOf(item("early", 0), item("late", 20_000)))
        assertEquals(listOf("early"), engine.frame(2_000, viewport).map { it.id })
        assertEquals(listOf("late"), engine.frame(22_000, viewport).map { it.id })
        assertEquals(listOf("early"), engine.frame(2_000, viewport).map { it.id })
        engine.clear()
        assertEquals(listOf("early"), engine.frame(2_500, viewport).map { it.id })
    }

    @Test fun previousSixMinuteSegmentSurvivesIntoNextSegment() {
        val engine = engine(listOf(item("tail", 359_000), item("next", 361_000)))
        assertTrue(engine.frame(362_000, viewport).any { it.id == "tail" })
        assertFalse(engine.frame(367_000, viewport).any { it.id == "tail" })
    }

    @Test fun scrollingFixedAndReverseModesUseDisjointCompatibleLanes() {
        val engine = engine((1..9).map { item("mode$it", 0, mode = it) })
        val frame = engine.frame(2_000, viewport)
        assertEquals((1..6).toSet(), frame.map { it.mode }.toSet())
        val top = frame.single { it.mode == 5 }
        val bottom = frame.single { it.mode == 4 }
        val scrolling = frame.filter { it.mode in 1..3 || it.mode == 6 }
        assertTrue(scrolling.all { it.top >= top.top + top.height })
        assertTrue(scrolling.all { it.top + it.height <= bottom.top })
        assertEquals(200f, top.x, 0.001f)
        assertEquals(50f, frame.single { it.mode == 6 }.x, 0.001f)
        assertEquals(350f, frame.single { it.mode == 1 }.x, 0.001f)
    }

    @Test fun widerFasterFollowerCannotCatchNarrowPredecessor() {
        val comments = listOf(item("narrow", 0), item("wide", 2_000))
        val engine = DanmakuLayoutEngine(comments, { text, _ -> if (text == "wide") 300f else 100f })
        // The predecessor has fully entered at 2s, but a 300px follower would catch it later.
        assertEquals(listOf("narrow"), engine.frame(2_500, oneScrollLane).map { it.id })
    }

    @Test fun safelySpacedFollowersCanShareALaneWithoutOverlap() {
        val engine = engine(listOf(item("first", 0), item("second", 2_000)))
        for (time in 2_000L..7_900L step 100L) {
            val frame = engine.frame(time, oneScrollLane)
            assertEquals(2, frame.size)
            val first = frame.single { it.id == "first" }
            val second = frame.single { it.id == "second" }
            assertEquals(first.lane, second.lane)
            assertTrue(first.x + first.width + 12f <= second.x + 0.001f)
        }
    }

    @Test fun reverseCommentsAlsoPreventCatchUpAndOpposingDirections() {
        val engine = DanmakuLayoutEngine(
            listOf(item("narrow", 0, mode = 6), item("wide", 2_000, mode = 6), item("opposite", 3_000)),
            { text, _ -> if (text == "wide") 300f else 100f },
        )
        assertEquals(listOf("narrow"), engine.frame(3_500, oneScrollLane).map { it.id })
    }

    @Test fun fixedCommentsExpireAndDoNotStackOnOneAnother() {
        val engine = engine(listOf(item("top1", 0, 5), item("top2", 1_000, 5), item("top3", 4_000, 5)))
        assertEquals(listOf("top1"), engine.frame(2_000, viewport).map { it.id })
        assertEquals(listOf("top3"), engine.frame(4_000, viewport).map { it.id })
        assertTrue(engine.frame(8_000, viewport).isEmpty())
    }

    @Test fun denseCommentsAndMeasurementCacheStayBounded() {
        val comments = (0..500).map { item("item$it", it * 500L) }
        val engine = DanmakuLayoutEngine(
            comments, { _, _ -> 10f },
            DanmakuLimits(maxVisibleItems = 3, maxMeasurements = 5, maxCandidatesPerFrame = 20),
        )
        for (time in 0L..250_000L step 500L) {
            assertTrue(engine.frame(time, viewport).size <= 3)
            assertTrue(engine.measurementCacheSize <= 5)
        }
    }

    @Test fun hugeTimestampBurstHasBoundedReplayWork() {
        var measurements = 0
        val engine = DanmakuLayoutEngine(
            (0..10_000).map { item("burst$it", 0) },
            { _, _ -> measurements++; 100f },
            DanmakuLimits(maxCandidatesPerFrame = 25),
        )
        assertTrue(engine.frame(500, viewport).size <= 14)
        assertTrue(measurements <= 25)
    }

    @Test fun unicodeTextIsBoundedWithoutSplittingEmojiAndControlsAreSingleLine() {
        val engine = engine(listOf(item("long", 0).copy(text = "😀".repeat(500)), item("lines", 0).copy(text = "a\nb\tc")))
        val frame = engine.frame(500, viewport)
        val longText = frame.single { it.id == "long" }.text
        assertEquals(120, longText.codePointCount(0, longText.length))
        assertEquals("😀".repeat(120), longText)
        assertEquals("a b c", frame.single { it.id == "lines" }.text)
    }

    @Test fun resizeReflowsPositionsAndTracksWithoutChangingMediaTime() {
        val engine = engine(listOf(item("moving", 0), item("fixed", 0, 5)))
        val small = engine.frame(2_000, oneScrollLane)
        val full = engine.frame(2_000, DanmakuViewport(1_000f, 600f, 1f))
        assertEquals(725f, full.single { it.id == "moving" }.x, 0.001f)
        assertEquals(450f, full.single { it.id == "fixed" }.x, 0.001f)
        assertTrue(full.single { it.id == "moving" }.x > small.single { it.id == "moving" }.x)
    }

    @Test fun smallInlineViewportRetainsFixedAndScrollingLanes() {
        val engine = engine(listOf(item("moving", 0), item("top", 0, 5), item("bottom", 0, 4)))
        val frame = engine.frame(500, DanmakuViewport(360f, 102f, 1f))
        assertEquals(3, frame.size)
        assertTrue(frame.all { it.top >= 0 && it.top + it.height <= 102 })
    }

    @Test fun invalidGeometryOrDisabledResetProducesNoStaleFrame() {
        val engine = engine(listOf(item("first", 0)))
        assertTrue(engine.frame(1_000, DanmakuViewport(0f, 400f, 1f)).isEmpty())
        assertTrue(engine.frame(1_000, DanmakuViewport(Float.NaN, 400f, 1f)).isEmpty())
        assertEquals(1, engine.frame(1_000, viewport).size)
        engine.clear()
        assertTrue(engine.frame(20_000, viewport).isEmpty())
    }

    private fun engine(items: List<DanmakuItem>) = DanmakuLayoutEngine(items, { _, _ -> 100f })

    private fun item(id: String, timeMs: Long, mode: Int = 1) = DanmakuItem(
        id = id, timeMs = timeMs, text = id, mode = mode, fontSize = 25, color = 0xFFFFFF,
    )
}
