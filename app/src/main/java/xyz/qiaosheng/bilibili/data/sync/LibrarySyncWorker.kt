package xyz.qiaosheng.bilibili.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CancellationException
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryScope

interface LibrarySyncScheduler { fun enqueue(accountId: Long) }

class WorkManagerLibraryScheduler @Inject constructor(@ApplicationContext private val context: Context) : LibrarySyncScheduler {
    override fun enqueue(accountId: Long) {
        if (accountId <= 0) return
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setInputData(workDataOf(LibrarySyncWorker.ACCOUNT_ID to accountId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // 接在进行中的任务后，避免“最后一次查空队列”和新意图入队之间的 KEEP 丢唤醒窗口。
        WorkManager.getInstance(context).enqueueUniqueWork("library-sync-$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryWorkerEntryPoint { fun libraryRepository(): LibraryRepository }

/** 队列只记录账号编号；Worker 每次执行重新校验当前会话，绝不恢复其他账号凭据。 */
class LibrarySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getLong(ACCOUNT_ID, 0)
        if (account <= 0) return Result.success()
        return try {
            val repository = EntryPointAccessors.fromApplication(applicationContext, LibraryWorkerEntryPoint::class.java).libraryRepository()
            if (repository.syncPending(account)) Result.retry() else Result.success()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { Result.retry() }
    }
    companion object { const val ACCOUNT_ID = "account_id" }
}
