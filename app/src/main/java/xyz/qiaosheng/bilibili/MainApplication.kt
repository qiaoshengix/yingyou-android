package xyz.qiaosheng.bilibili

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import xyz.qiaosheng.bilibili.data.repository.LibraryRepository

@HiltAndroidApp
class MainApplication: Application() {
    @Inject lateinit var libraryRepository: LibraryRepository

    override fun onCreate() {
        super.onCreate()
        // 不要求用户先进入资料库；进程恢复后也要继续处理已经落盘的同步意图。
        libraryRepository.start()
    }
}
