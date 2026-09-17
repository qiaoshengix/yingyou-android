package xyz.qiaosheng.bilibili.service

data class TimerServiceState(
    val elapsedSeconds: Long = 0L,
    val isRunning: Boolean = false,
    val lifecycleEvents: List<String> = emptyList()
)
