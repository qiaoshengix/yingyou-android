package xyz.qiaosheng.bilibili.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import xyz.qiaosheng.bilibili.MainActivity

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineDownloadService : DownloadService(OFFLINE_NOTIFICATION_ID, 1_000L) {
    @Inject lateinit var repository: OfflineRepository

    override fun onCreate() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "离线视频缓存", NotificationManager.IMPORTANCE_LOW)
        )
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager = repository.downloadManagerForService()
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
            this, android.R.drawable.stat_sys_download, intent, "正在缓存视频与音频", downloads, notMetRequirements
        )
    }

    companion object {
        const val CHANNEL_ID = "offline-downloads"
        const val OFFLINE_NOTIFICATION_ID = 7412
    }
}
