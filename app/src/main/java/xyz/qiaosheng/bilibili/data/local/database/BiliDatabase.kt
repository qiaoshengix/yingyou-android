package xyz.qiaosheng.bilibili.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import xyz.qiaosheng.bilibili.data.local.dao.LibraryDao
import xyz.qiaosheng.bilibili.data.local.dao.RecommendDao
import xyz.qiaosheng.bilibili.data.local.dao.SearchHistoryDao
import xyz.qiaosheng.bilibili.data.local.entity.FavoriteFolderEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryEntryEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryOutboxEntity
import xyz.qiaosheng.bilibili.data.local.entity.RecommendRemoteKeyEntity
import xyz.qiaosheng.bilibili.data.local.entity.RecommendVideoEntity
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity
import xyz.qiaosheng.bilibili.data.local.entity.WatchProgressEntity

@Database(
    entities = [SearchHistoryEntity::class,
        LibraryEntryEntity::class,
        FavoriteFolderEntity::class,
        LibraryOutboxEntity::class,
        WatchProgressEntity::class,
        RecommendVideoEntity::class,
        RecommendRemoteKeyEntity::class
    ], version = 3, exportSchema = false
)
abstract class BiliDatabase : RoomDatabase() {
    abstract fun SearchHistoryDao(): SearchHistoryDao
    abstract fun libraryDao(): LibraryDao

    abstract fun recommendDao(): RecommendDao

    companion object {
        /** 只新增账号隔离表，不重建或清空既有搜索历史。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS library_entries (
                    accountId INTEGER NOT NULL, kind TEXT NOT NULL, folderId INTEGER NOT NULL,
                    bvid TEXT NOT NULL, aid INTEGER NOT NULL, cid INTEGER NOT NULL, title TEXT NOT NULL,
                    coverUrl TEXT NOT NULL, ownerName TEXT NOT NULL, durationSeconds INTEGER NOT NULL,
                    progressSeconds INTEGER NOT NULL, viewedAt INTEGER NOT NULL, sortAt INTEGER NOT NULL,
                    modifiedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, pending INTEGER NOT NULL,
                    generation TEXT NOT NULL, PRIMARY KEY(accountId, kind, folderId, bvid))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS library_folders (
                    accountId INTEGER NOT NULL, folderId INTEGER NOT NULL, title TEXT NOT NULL,
                    mediaCount INTEGER NOT NULL, PRIMARY KEY(accountId, folderId))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS library_outbox (
                    accountId INTEGER NOT NULL, kind TEXT NOT NULL, folderId INTEGER NOT NULL,
                    bvid TEXT NOT NULL, mutationId TEXT NOT NULL, desired INTEGER NOT NULL,
                    aid INTEGER NOT NULL, cid INTEGER NOT NULL, progressSeconds INTEGER NOT NULL,
                    completed INTEGER NOT NULL, createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL,
                    state TEXT NOT NULL, error TEXT, PRIMARY KEY(accountId, kind, folderId, bvid))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS watch_progress (
                    accountId INTEGER NOT NULL, bvid TEXT NOT NULL, cid INTEGER NOT NULL,
                    positionSeconds INTEGER NOT NULL, completed INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(accountId, bvid, cid))"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS recommend_videos (
                accountId INTEGER NOT NULL,
                bvid TEXT NOT NULL,
                title TEXT NOT NULL,
                coverUrl TEXT NOT NULL,
                duration INTEGER NOT NULL,
                ownerName TEXT NOT NULL,
                ownerAvatar TEXT NOT NULL,
                playCount INTEGER NOT NULL,
                danmakuCount INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(accountId, bvid)
            )
        """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS recommend_remote_keys (
                accountId INTEGER NOT NULL,
                nextCursor INTEGER,
                nextPosition INTEGER NOT NULL,
                PRIMARY KEY(accountId)
            )
        """.trimIndent()
                )
            }
        }


        val HISTORY_RETENTION_CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Existing installations may already contain more than the UI's old LIMIT 20.
                db.execSQL(SearchHistoryDao.PRUNE_QUERY)
            }
        }
    }
}
