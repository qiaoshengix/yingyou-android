package xyz.qiaosheng.bilibili.model.profile

data class UserProfile(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
    val sign: String,
    val level: Int,
    val followingCount: Int,
    val followerCount: Int,
    val coins: Int
)
