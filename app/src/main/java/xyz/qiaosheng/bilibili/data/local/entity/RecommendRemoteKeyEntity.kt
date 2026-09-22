package xyz.qiaosheng.bilibili.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommend_remote_keys")
data class RecommendRemoteKeyEntity(
    @PrimaryKey val accountId: Long,
    val nextCursor: Int?,
    val nextPosition: Long
)
