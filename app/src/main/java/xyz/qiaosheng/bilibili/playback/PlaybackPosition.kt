package xyz.qiaosheng.bilibili.playback

/** 服务端 -1 表示看完；看完或进度越界时从头播放，避免打开后立即结束。 */
internal fun resumePositionMillis(savedSeconds: Long, durationSeconds: Long): Long {
    if (savedSeconds <= 0L || durationSeconds <= 0L) return 0L
    if (savedSeconds >= durationSeconds - 1L) return 0L
    return savedSeconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
}
