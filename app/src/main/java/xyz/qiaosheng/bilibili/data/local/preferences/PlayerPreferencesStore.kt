package xyz.qiaosheng.bilibili.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.playerPreferencesDataStore by preferencesDataStore(name = "player")

/** 播放习惯独立于账号 Cookie 保存，退出登录后仍保留用户选择。 */
@Singleton
class PlayerPreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    writeScope: CoroutineScope,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.playerPreferencesDataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val queueLock = Any()
    private var latestWrite: WriteRequest? = null
    private val writes = Channel<WriteRequest>(
        capacity = Channel.CONFLATED,
        onUndeliveredElement = { finish(it) },
    )

    init {
        // 单例持有应用存续的写队列；页面销毁只取消等待结果，不取消已接收的用户选择。
        writeScope.launch {
            for (request in writes) {
                try {
                    dataStore.edit { it[DANMAKU_ENABLED] = request.enabled }
                    finish(request)
                } catch (cancelled: CancellationException) {
                    finish(request, cancelled)
                    throw cancelled
                } catch (error: Exception) {
                    finish(request, error)
                }
            }
        }.invokeOnCompletion { writes.cancel() }
    }

    val danmakuEnabled: Flow<Boolean> = flow {
        // 新页面必须先等先前已入队的选择落盘，避免按旧开关发起弹幕请求。
        while (true) {
            val pending = synchronized(queueLock) { latestWrite } ?: break
            pending.result.await()
        }
        emitAll(dataStore.data.map { it[DANMAKU_ENABLED] ?: true }.distinctUntilChanged())
    }

    /** 同步接收最新选择；结果完成表示已保存，或已被之后的排队选择替代。 */
    fun enqueueDanmakuEnabled(enabled: Boolean): Deferred<Unit> {
        val request = WriteRequest(enabled)
        synchronized(queueLock) {
            latestWrite = request
            val result = writes.trySend(request)
            if (result.isFailure) finish(request, result.exceptionOrNull()
                ?: CancellationException("Player preference writer is unavailable"))
        }
        return request.result
    }

    suspend fun setDanmakuEnabled(enabled: Boolean) {
        enqueueDanmakuEnabled(enabled).await()
    }

    private fun finish(request: WriteRequest, error: Throwable? = null) {
        synchronized(queueLock) {
            if (latestWrite === request) latestWrite = null
        }
        if (error == null) request.result.complete(Unit) else request.result.completeExceptionally(error)
    }

    private class WriteRequest(val enabled: Boolean) {
        // 无页面 Job 作为 parent，取消 await 不会取消应用队列中的写入。
        val result = CompletableDeferred<Unit>()
    }

    private companion object {
        val DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
    }
}
