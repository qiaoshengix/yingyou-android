package xyz.qiaosheng.bilibili.ui.servicelab

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import xyz.qiaosheng.bilibili.service.StudyTimerService
import xyz.qiaosheng.bilibili.service.TimerServiceState

@HiltViewModel
class ServiceLabViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val connectedService =
        MutableStateFlow<StudyTimerService?>(null)

    private val _isBound = MutableStateFlow(false)
    val isBound = _isBound

    private var bindingRequested = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            val localBinder = binder as StudyTimerService.LocalBinder
            connectedService.value = localBinder.getService()
            _isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectedService.value = null
            _isBound.value = false
            bindingRequested = false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val serviceState = connectedService
        .flatMapLatest { service ->
            service?.state ?: flowOf(TimerServiceState())
        }
        .combine(StudyTimerService.lifecycleEvents) { state, events ->
            state.copy(lifecycleEvents = events)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TimerServiceState()
        )

    fun startService() {
        val intent = Intent(context, StudyTimerService::class.java)
            .setAction(StudyTimerService.ACTION_START)
        context.startService(intent)
    }

    fun stopService() {
        context.stopService(
            Intent(context, StudyTimerService::class.java)
        )
    }

    fun bindService() {
        if (bindingRequested || _isBound.value) return

        bindingRequested = context.bindService(
            Intent(context, StudyTimerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbindService() {
        if (!bindingRequested) return

        context.unbindService(connection)
        bindingRequested = false
        connectedService.value = null
        _isBound.value = false
    }

    fun startThroughBinder() {
        connectedService.value?.startTimer()
    }

    fun pauseThroughBinder() {
        connectedService.value?.pauseTimer()
    }

    fun resetThroughBinder() {
        connectedService.value?.resetTimer()
    }

    fun clearLifecycleEvents() {
        StudyTimerService.clearLifecycleEvents()
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        if (bindingRequested) {
            context.unbindService(connection)
            bindingRequested = false
        }
        super.onCleared()
    }
}
