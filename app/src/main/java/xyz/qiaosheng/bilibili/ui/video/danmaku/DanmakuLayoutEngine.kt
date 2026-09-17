package xyz.qiaosheng.bilibili.ui.video.danmaku

import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem
import kotlin.math.floor

/** Layout is driven only by media time; wall-clock time and playback speed never accumulate drift. */
internal class DanmakuLayoutEngine(
    items: List<DanmakuItem>,
    private val measureText: (String, Float) -> Float,
    private val limits: DanmakuLimits = DanmakuLimits(),
) {
    private val timeline = items.asSequence()
        .filter { it.timeMs >= 0 && it.mode in 1..6 }
        .map { Candidate(it, displayText(it.text, limits.maxTextCodePoints)) }
        .filter { it.text.isNotBlank() }
        .sortedWith(compareBy<Candidate> { it.item.timeMs }.thenBy { it.item.id })
        .toList()
    private val active = ArrayList<ActiveItem>()
    private val measurements = object : LinkedHashMap<MeasurementKey, Float>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MeasurementKey, Float>?): Boolean =
            size > limits.maxMeasurements
    }
    private var viewport: DanmakuViewport? = null
    private var lanes: List<Lane> = emptyList()
    private var nextIndex = 0
    private var lastPositionMs: Long? = null

    internal val measurementCacheSize: Int get() = measurements.size

    /** Also called for explicit seeks, including short forward seeks that resemble normal playback. */
    fun clear() {
        active.clear()
        lastPositionMs = null
        nextIndex = 0
    }

    fun frame(positionMs: Long, viewport: DanmakuViewport): List<PlacedDanmaku> {
        if (!viewport.isUsable || positionMs < 0) {
            clear()
            return emptyList()
        }
        if (this.viewport != viewport) {
            if (this.viewport?.density != viewport.density) measurements.clear()
            this.viewport = viewport
            lanes = createLanes(viewport)
            clear()
        }
        if (lanes.isEmpty()) return emptyList()

        val lastPosition = lastPositionMs
        val discontinuity = lastPosition == null || positionMs < lastPosition ||
            positionMs - lastPosition > MAX_FRAME_GAP_MS
        val endIndex = upperBound(positionMs)
        if (discontinuity) {
            active.clear()
            // Reconstruct occupancy before the visible window as well as its surviving comments.
            // This deliberately crosses API segment boundaries (for example, 359s -> 361s).
            nextIndex = lowerBound((positionMs - limits.scrollDurationMs * 2).coerceAtLeast(0))
        }
        // Bound the work of seeks and exceptionally dense timestamps, in addition to visible items.
        nextIndex = maxOf(nextIndex, endIndex - limits.maxCandidatesPerFrame)
        while (nextIndex < endIndex) {
            val candidate = timeline[nextIndex++]
            active.removeAll { it.endMs <= candidate.item.timeMs }
            place(candidate, viewport)?.let(active::add)
        }
        active.removeAll { it.endMs <= positionMs }
        lastPositionMs = positionMs
        return active.map { placed ->
            PlacedDanmaku(
                id = placed.candidate.item.id,
                text = placed.candidate.text,
                mode = placed.candidate.item.mode,
                color = placed.candidate.item.color,
                x = placed.xAt(positionMs, viewport.widthPx),
                top = placed.lane.top,
                width = placed.width,
                height = placed.lane.height,
                fontSizePx = placed.fontSizePx,
                lane = placed.lane.index,
            )
        }
    }

    private fun place(candidate: Candidate, viewport: DanmakuViewport): ActiveItem? {
        if (active.size >= limits.maxVisibleItems) return null
        val item = candidate.item
        val kind = when (item.mode) {
            4 -> LaneKind.BOTTOM
            5 -> LaneKind.TOP
            else -> LaneKind.SCROLL
        }
        val fontSize = 18f * viewport.layoutDensity * (item.fontSize / 25f).coerceIn(0.8f, 1.5f)
        val key = MeasurementKey(candidate.text, fontSize)
        val width = measurements.getOrPut(key) {
            (measureText(candidate.text, fontSize).takeIf { it.isFinite() && it > 0f } ?: 1f)
                .coerceAtMost(MAX_MEASURED_WIDTH_PX)
        }
        val duration = if (kind == LaneKind.SCROLL) limits.scrollDurationMs else limits.fixedDurationMs
        val matchingLane = lanes.firstOrNull { lane ->
            lane.kind == kind && active.none { previous ->
                previous.lane.index == lane.index &&
                    !canShareLane(previous, candidate, width, duration, viewport)
            }
        } ?: return null
        return ActiveItem(candidate, width, fontSize, matchingLane, duration)
    }

    private fun canShareLane(
        previous: ActiveItem,
        candidate: Candidate,
        width: Float,
        durationMs: Long,
        viewport: DanmakuViewport,
    ): Boolean {
        if (previous.lane.kind != LaneKind.SCROLL) return false
        val reverse = candidate.item.mode == 6
        // Opposing directions must never enter the same occupied lane.
        if ((previous.candidate.item.mode == 6) != reverse) return false
        val age = candidate.item.timeMs - previous.candidate.item.timeMs
        val previousSpeed = (viewport.widthPx + previous.width) / previous.durationMs
        val newSpeed = (viewport.widthPx + width) / durationMs
        val gap = 12f * viewport.density
        // In travel-relative coordinates, both directions have the same right-to-left geometry.
        val previousTrailingEdge = viewport.widthPx - previousSpeed * age + previous.width
        if (previousTrailingEdge + gap > viewport.widthPx) return false
        if (newSpeed <= previousSpeed) return true
        val remaining = previous.durationMs - age
        // Check the other end of the linear separation interval: a wide, faster comment must not
        // catch a narrow predecessor before the predecessor has completely left the viewport.
        return viewport.widthPx - newSpeed * remaining >= gap
    }

    private fun createLanes(viewport: DanmakuViewport): List<Lane> {
        val padding = 8f * viewport.layoutDensity
        val laneHeight = 34f * viewport.layoutDensity
        val count = floor((viewport.heightPx - padding * 2) / laneHeight).toInt()
            .coerceIn(0, limits.maxLanes)
        if (count == 0) return emptyList()
        val hasFixedLanes = count >= 3
        return List(count) { index ->
            val kind = when {
                hasFixedLanes && index == 0 -> LaneKind.TOP
                hasFixedLanes && index == count - 1 -> LaneKind.BOTTOM
                else -> LaneKind.SCROLL
            }
            val top = if (kind == LaneKind.BOTTOM) viewport.heightPx - padding - laneHeight
            else padding + laneHeight * index
            Lane(index, kind, top, laneHeight)
        }
    }

    private fun lowerBound(positionMs: Long): Int {
        var low = 0
        var high = timeline.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (timeline[middle].item.timeMs < positionMs) low = middle + 1 else high = middle
        }
        return low
    }

    private fun upperBound(positionMs: Long): Int {
        var low = 0
        var high = timeline.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (timeline[middle].item.timeMs <= positionMs) low = middle + 1 else high = middle
        }
        return low
    }

    private data class Candidate(val item: DanmakuItem, val text: String)
    private data class MeasurementKey(val text: String, val fontSize: Float)
    private enum class LaneKind { TOP, SCROLL, BOTTOM }
    private data class Lane(val index: Int, val kind: LaneKind, val top: Float, val height: Float)
    private data class ActiveItem(
        val candidate: Candidate,
        val width: Float,
        val fontSizePx: Float,
        val lane: Lane,
        val durationMs: Long,
    ) {
        val endMs: Long = candidate.item.timeMs + durationMs

        fun xAt(positionMs: Long, viewportWidth: Float): Float {
            val fraction = (positionMs - candidate.item.timeMs).toFloat() / durationMs
            return when (candidate.item.mode) {
                4, 5 -> (viewportWidth - width) / 2
                6 -> -width + (viewportWidth + width) * fraction
                else -> viewportWidth - (viewportWidth + width) * fraction
            }
        }
    }

    private companion object {
        const val MAX_FRAME_GAP_MS = 1_000L
        const val MAX_MEASURED_WIDTH_PX = 65_536f

        fun displayText(text: String, maxCodePoints: Int): String {
            var end = 0
            repeat(maxCodePoints) {
                if (end < text.length) end += Character.charCount(text.codePointAt(end))
            }
            return text.substring(0, end).map { if (it.isISOControl()) ' ' else it }.joinToString("")
        }
    }
}

internal data class DanmakuViewport(val widthPx: Float, val heightPx: Float, val density: Float) {
    val isUsable: Boolean get() = widthPx.isFinite() && heightPx.isFinite() && density.isFinite() &&
        widthPx > 0 && heightPx > 0 && density > 0
    // Small inline players still keep all three kinds of lane, without shrinking text indefinitely.
    val layoutDensity: Float get() = minOf(density, heightPx / 119f).coerceAtLeast(density * 0.65f)
}

internal data class DanmakuLimits(
    val scrollDurationMs: Long = 8_000,
    val fixedDurationMs: Long = 4_000,
    val maxVisibleItems: Int = 64,
    val maxLanes: Int = 14,
    val maxTextCodePoints: Int = 120,
    val maxMeasurements: Int = 512,
    val maxCandidatesPerFrame: Int = 4_096,
)

internal data class PlacedDanmaku(
    val id: String,
    val text: String,
    val mode: Int,
    val color: Int,
    val x: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val fontSizePx: Float,
    val lane: Int,
)
