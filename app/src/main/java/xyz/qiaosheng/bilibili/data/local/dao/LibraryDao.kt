package xyz.qiaosheng.bilibili.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import xyz.qiaosheng.bilibili.data.local.entity.FavoriteFolderEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryEntryEntity
import xyz.qiaosheng.bilibili.data.local.entity.LibraryOutboxEntity
import xyz.qiaosheng.bilibili.data.local.entity.WatchProgressEntity

@Dao
abstract class LibraryDao {
    @Query("SELECT * FROM library_entries WHERE accountId=:accountId AND kind=:kind AND folderId=:folderId AND deleted=0 ORDER BY sortAt DESC, bvid")
    abstract fun observeEntries(accountId: Long, kind: String, folderId: Long): Flow<List<LibraryEntryEntity>>
    @Query("SELECT * FROM library_entries WHERE accountId=:accountId AND bvid=:bvid")
    abstract fun observeVideo(accountId: Long, bvid: String): Flow<List<LibraryEntryEntity>>
    @Query("SELECT * FROM library_folders WHERE accountId=:accountId ORDER BY folderId")
    abstract fun observeFolders(accountId: Long): Flow<List<FavoriteFolderEntity>>
    @Query("SELECT * FROM library_outbox WHERE accountId=:accountId ORDER BY createdAt, bvid")
    abstract fun observeOutbox(accountId: Long): Flow<List<LibraryOutboxEntity>>
    @Query("SELECT * FROM library_entries WHERE accountId=:accountId AND kind=:kind AND folderId=:folderId AND bvid=:bvid")
    abstract suspend fun entry(accountId: Long, kind: String, folderId: Long, bvid: String): LibraryEntryEntity?
    @Query("SELECT * FROM library_outbox WHERE accountId=:accountId ORDER BY createdAt, bvid")
    abstract suspend fun outbox(accountId: Long): List<LibraryOutboxEntity>
    @Query("SELECT * FROM library_folders WHERE accountId=:accountId ORDER BY folderId")
    abstract suspend fun folders(accountId: Long): List<FavoriteFolderEntity>
    @Query("SELECT * FROM watch_progress WHERE accountId=:accountId AND bvid=:bvid AND cid=:cid")
    abstract suspend fun progress(accountId: Long, bvid: String, cid: Long): WatchProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putEntry(value: LibraryEntryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putOutbox(value: LibraryOutboxEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putFolders(values: List<FavoriteFolderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putProgress(value: WatchProgressEntity)
    @Query("DELETE FROM library_outbox WHERE accountId=:accountId AND mutationId=:mutationId")
    abstract suspend fun deleteOutbox(accountId: Long, mutationId: String)
    @Query("DELETE FROM library_folders WHERE accountId=:accountId")
    abstract suspend fun deleteFolders(accountId: Long)
    @Query("DELETE FROM watch_progress WHERE accountId=:accountId AND bvid=:bvid")
    abstract suspend fun deleteProgress(accountId: Long, bvid: String)
    @Query("DELETE FROM library_entries WHERE accountId=:accountId AND kind=:kind AND folderId=:folderId AND pending=0 AND deleted=0 AND generation!=:generation AND modifiedAt<=:startedAt")
    abstract suspend fun pruneRemoteMissing(accountId: Long, kind: String, folderId: Long, generation: String, startedAt: Long)

    /** 乐观状态与待同步意图必须原子提交，进程在任一步骤崩溃都不能丢失用户意图。 */
    @Transaction
    open suspend fun mutate(entry: LibraryEntryEntity, operation: LibraryOutboxEntity?, progress: WatchProgressEntity? = null) {
        putEntry(entry)
        if (operation != null) putOutbox(operation)
        if (progress != null) putProgress(progress)
        if (entry.kind == "HISTORY" && entry.deleted) deleteProgress(entry.accountId, entry.bvid)
    }

    @Transaction
    open suspend fun replaceFolders(accountId: Long, values: List<FavoriteFolderEntity>) {
        deleteFolders(accountId)
        putFolders(values)
    }

    /** 稀疏页不能推断删除；仅完整读取一个收藏夹后清理该夹的失效缓存。 */
    @Transaction
    open suspend fun mergePage(
        accountId: Long, kind: String, folderId: Long, values: List<LibraryEntryEntity>,
        generation: String, startedAt: Long, complete: Boolean, verifiedLikes: Set<String> = emptySet()
    ) {
        values.forEach { incoming ->
            val current = entry(accountId, kind, folderId, incoming.bvid)
            val accept = current == null || (!current.pending && current.modifiedAt <= startedAt &&
                !(current.deleted && if (kind == "LIKES") incoming.bvid !in verifiedLikes else incoming.sortAt <= current.modifiedAt) &&
                !(kind == "HISTORY" && current.sortAt > incoming.sortAt))
            if (accept) {
                putEntry(incoming.copy(generation = generation))
                if (kind == "HISTORY" && incoming.cid > 0) {
                    val saved = progress(accountId, incoming.bvid, incoming.cid)
                    if (saved == null || saved.updatedAt < incoming.sortAt) {
                        putProgress(WatchProgressEntity(accountId, incoming.bvid, incoming.cid,
                            incoming.progressSeconds.coerceAtLeast(0), incoming.progressSeconds < 0 ||
                                incoming.durationSeconds > 0 && incoming.progressSeconds >= incoming.durationSeconds, incoming.sortAt))
                    }
                }
            } else {
                putEntry(current.copy(generation = generation))
            }
        }
        if (complete && kind == "FAVORITES") pruneRemoteMissing(accountId, kind, folderId, generation, startedAt)
    }

    @Transaction
    open suspend fun acknowledge(operation: LibraryOutboxEntity) {
        if (outbox(operation.accountId).none { it.mutationId == operation.mutationId }) return
        deleteOutbox(operation.accountId, operation.mutationId)
        entry(operation.accountId, operation.kind, operation.folderId, operation.bvid)?.let { putEntry(it.copy(pending = false)) }
    }

    @Transaction
    open suspend fun fail(operation: LibraryOutboxEntity, state: String, error: String) {
        if (outbox(operation.accountId).any { it.mutationId == operation.mutationId }) {
            putOutbox(operation.copy(attempts = operation.attempts + 1, state = state, error = error))
        }
    }

    @Transaction
    open suspend fun retryBlocked(accountId: Long, includePermanent: Boolean) {
        outbox(accountId).filter { it.state == "LOGIN_REQUIRED" || includePermanent && it.state == "FAILED" }
            .forEach { putOutbox(it.copy(state = "PENDING", error = null)) }
    }
}
