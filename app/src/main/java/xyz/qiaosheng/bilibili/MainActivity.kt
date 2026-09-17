package xyz.qiaosheng.bilibili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.offline.OfflineRepository
import xyz.qiaosheng.bilibili.ui.main.BilibiliApp

/** Android 系统入口装配 Compose 并协调前台下载时机；主题和导航交由应用根组件管理。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var offlineRepository: OfflineRepository
    @Inject lateinit var errorReporter: ErrorReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BilibiliApp() }
    }

    override fun onStart() {
        super.onStart()
        // 用户回到前台后恢复下载；后台同步 Worker 唤醒进程时不强行启动前台下载服务。
        lifecycleScope.launch {
            try { offlineRepository.onAppForeground() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { errorReporter.message("恢复下载队列失败", error) }
        }
    }

    override fun onStop() {
        offlineRepository.onAppBackground()
        super.onStop()
    }
}
