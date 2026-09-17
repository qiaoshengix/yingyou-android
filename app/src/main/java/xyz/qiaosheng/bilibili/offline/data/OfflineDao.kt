package xyz.qiaosheng.bilibili.offline.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_videos WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observe(accountId: Long): Flow<List<OfflineVideoEntity>>

    @Query("SELECT * FROM offline_videos ORDER BY createdAt DESC")
    suspend fun getAll(): List<OfflineVideoEntity>

    @Query("SELECT * FROM offline_videos WHERE id = :id AND accountId = :accountId")
    suspend fun get(id: String, accountId: Long): OfflineVideoEntity?

    @Query("SELECT * FROM offline_videos WHERE accountId = :accountId AND bvid = :bvid AND (:cid IS NULL OR cid = :cid) AND deleting = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun find(accountId: Long, bvid: String, cid: Long?): OfflineVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(video: OfflineVideoEntity)

    @Query("UPDATE offline_videos SET status = :status, downloadedBytes = :downloaded, totalBytes = :total, errorMessage = :error WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, downloaded: Long, total: Long, error: String?)

    @Query("UPDATE offline_videos SET userPaused = :paused, status = :status, errorMessage = NULL WHERE id = :id AND accountId = :accountId")
    suspend fun setPaused(id: String, accountId: Long, paused: Boolean, status: String)

    @Query("UPDATE offline_videos SET deleting = 1, status = 'REMOVING' WHERE id = :id AND accountId = :accountId")
    suspend fun markRemoving(id: String, accountId: Long)

    @Query("DELETE FROM offline_videos WHERE id = :id")
    suspend fun delete(id: String)
}
