package xyz.qiaosheng.bilibili.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.qiaosheng.bilibili.data.local.dao.SearchHistoryDao
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class BiliDataModule {

    @Provides
    @Singleton
    fun biliDatabaseProvides(
        @ApplicationContext context: Context
    ): BiliDatabase {
        return Room.databaseBuilder(context, BiliDatabase::class.java, "bili_database")
            .addMigrations(
                BiliDatabase.MIGRATION_1_2,
                BiliDatabase.MIGRATION_2_3
            )
            .addCallback(BiliDatabase.HISTORY_RETENTION_CALLBACK)
            .build()
    }
    @Provides
    @Singleton
    fun searchHistoryProvides(biliDatabase: BiliDatabase): SearchHistoryDao {
        return biliDatabase.SearchHistoryDao()
    }
}
