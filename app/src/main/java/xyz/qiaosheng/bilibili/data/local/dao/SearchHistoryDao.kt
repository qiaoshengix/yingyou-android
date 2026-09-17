package xyz.qiaosheng.bilibili.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity

/** 搜索历史的持久化入口；去重、排序和保留上限统一在数据库层执行。 */
@Dao
interface SearchHistoryDao {

    companion object {
        const val MAX_HISTORY_SIZE = 20
        // REPLACE gives a keyword a new rowid, so searches in the same millisecond keep their order.
        const val RECENT_ORDER = "searchTime DESC, rowid DESC"
        const val PRUNE_QUERY = "DELETE FROM search_history WHERE rowid NOT IN " +
            "(SELECT rowid FROM search_history ORDER BY " + RECENT_ORDER + " LIMIT " +
            MAX_HISTORY_SIZE + ")"
    }

    @Query("SELECT * FROM search_history ORDER BY " + RECENT_ORDER + " LIMIT " + MAX_HISTORY_SIZE)
    fun getAll(): Flow<List<SearchHistoryEntity>>

    /** 写入与清理在同一事务中完成，避免列表短暂出现超过上限的记录。 */
    @Transaction
    suspend fun insert(entity: SearchHistoryEntity) {
        insertOrReplace(entity)
        prune()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SearchHistoryEntity)

    @Query(PRUNE_QUERY)
    suspend fun prune()

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
