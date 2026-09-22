package xyz.qiaosheng.bilibili.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "recommend_videos",
    primaryKeys = ["accountId", "bvid"]
)
data class RecommendVideoEntity(
    val accountId: Long,
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val duration: Int,
    val ownerName: String,
    val ownerAvatar: String,
    val playCount: Long,
    val danmakuCount: Long,
    val position: Long
)
