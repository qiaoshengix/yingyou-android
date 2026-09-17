package xyz.qiaosheng.bilibili.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import xyz.qiaosheng.bilibili.offline.data.OfflineDatabase

@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): OfflineDatabase =
        Room.databaseBuilder(context, OfflineDatabase::class.java, "offline-videos.db").build()

    @Provides
    fun dao(database: OfflineDatabase) = database.offlineDao()
}
