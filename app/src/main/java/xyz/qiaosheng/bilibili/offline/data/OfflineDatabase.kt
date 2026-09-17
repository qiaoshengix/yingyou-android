package xyz.qiaosheng.bilibili.offline.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OfflineVideoEntity::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao
}
