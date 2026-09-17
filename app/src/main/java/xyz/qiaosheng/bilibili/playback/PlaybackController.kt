package xyz.qiaosheng.bilibili.playback

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 连接由 ViewModel 持有和释放；取消一次加载不会释放仍可复用的连接。 */
suspend fun ListenableFuture<MediaController>.awaitPlaybackController(): MediaController =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                if (continuation.isActive) {
                    runCatching { get() }.fold(continuation::resume, continuation::resumeWithException)
                }
            },
            MoreExecutors.directExecutor()
        )
    }
