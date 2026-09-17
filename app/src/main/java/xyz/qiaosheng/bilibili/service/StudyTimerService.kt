package xyz.qiaosheng.bilibili.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StudyTimerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): StudyTimerService = this@StudyTimerService
    }

    private val binder = LocalBinder()

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private var timerJob: Job? = null

    private val _state = MutableStateFlow(TimerServiceState())
    val state = _state.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        recordLifecycle("onCreate")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        recordLifecycle("onStartCommand(startId=$startId)")

        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> resetTimer()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        recordLifecycle("onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        recordLifecycle("onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        recordLifecycle("onDestroy")
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startTimer() {
        if (timerJob?.isActive == true) return

        _state.update { it.copy(isRunning = true) }
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1_000L.milliseconds)
                _state.update { current ->
                    current.copy(elapsedSeconds = current.elapsedSeconds + 1L)
                }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        pauseTimer()
        _state.update { it.copy(elapsedSeconds = 0L) }
    }

    private fun recordLifecycle(event: String) {
        val time = LocalTime.now().format(TIME_FORMATTER)
        val message = "$time  $event"

        _lifecycleEvents.update { current ->
            (current + message).takeLast(MAX_EVENT_COUNT)
        }

        _state.update { current ->
            current.copy(
                lifecycleEvents = (current.lifecycleEvents + message)
                    .takeLast(MAX_EVENT_COUNT)
            )
        }
    }

    companion object {
        const val ACTION_START =
            "xyz.qiaosheng.bilibili.action.START_TIMER"
        const val ACTION_PAUSE =
            "xyz.qiaosheng.bilibili.action.PAUSE_TIMER"
        const val ACTION_RESET =
            "xyz.qiaosheng.bilibili.action.RESET_TIMER"

        private const val MAX_EVENT_COUNT = 30
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

        private val _lifecycleEvents =
            MutableStateFlow<List<String>>(emptyList())
        val lifecycleEvents = _lifecycleEvents.asStateFlow()

        fun clearLifecycleEvents() {
            _lifecycleEvents.value = emptyList()
        }
    }
}
