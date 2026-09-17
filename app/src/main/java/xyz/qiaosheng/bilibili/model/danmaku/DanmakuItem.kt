package xyz.qiaosheng.bilibili.model.danmaku

/** Presentation-neutral video comment; [timeMs] is the absolute video position. */
data class DanmakuItem(
    val id: String,
    val timeMs: Long,
    val text: String,
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val weight: Int = 0,
)

const val DANMAKU_SEGMENT_DURATION_MS: Long = 6 * 60 * 1_000L

/** The API uses one-based, six-minute segments. */
fun danmakuSegmentIndex(positionMs: Long): Int =
    (positionMs.coerceAtLeast(0) / DANMAKU_SEGMENT_DURATION_MS)
        .coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt() + 1
