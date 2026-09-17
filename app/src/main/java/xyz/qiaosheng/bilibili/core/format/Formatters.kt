package xyz.qiaosheng.bilibili.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 播放量格式化：1.2万、3.5亿
fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "${count / 100_000_000}.${(count / 10_000_000) % 10}亿"
        count >= 10_000 -> "${count / 10_000}.${(count / 1_000) % 10}万"
        else -> count.toString()
    }
}

// 时长格式化：12:34
fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%02d:%02d".format(min, sec)
}

fun formatPublishTime(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

fun formatRelativeTime(epochSeconds: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val timestampMillis = if (epochSeconds > 10_000_000_000L) epochSeconds else epochSeconds * 1000
    val elapsedSeconds = ((nowMillis - timestampMillis) / 1000).coerceAtLeast(0)
    return when {
        elapsedSeconds < 60 -> "刚刚"
        elapsedSeconds < 3_600 -> "${elapsedSeconds / 60}分钟前"
        elapsedSeconds < 86_400 -> "${elapsedSeconds / 3_600}小时前"
        elapsedSeconds < 604_800 -> "${elapsedSeconds / 86_400}天前"
        else -> Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}

fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
