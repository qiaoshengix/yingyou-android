package xyz.qiaosheng.bilibili.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import xyz.qiaosheng.bilibili.data.local.entity.RecommendRemoteKeyEntity
import xyz.qiaosheng.bilibili.data.local.entity.RecommendVideoEntity

@Dao
interface RecommendDao {

    @Query("""
        SELECT * FROM recommend_videos
        WHERE accountId = :accountId
        ORDER BY position ASC, bvid ASC
    """)
    fun pagingSource(
        accountId: Long
    ): PagingSource<Int, RecommendVideoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideos(videos: List<RecommendVideoEntity>)

    @Query("""
        DELETE FROM recommend_videos
        WHERE accountId = :accountId
    """)
    suspend fun clearVideos(accountId: Long)

    @Query("""
        SELECT * FROM recommend_remote_keys
        WHERE accountId = :accountId
    """)
    suspend fun remoteKey(accountId: Long): RecommendRemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRemoteKey(key: RecommendRemoteKeyEntity)
}
