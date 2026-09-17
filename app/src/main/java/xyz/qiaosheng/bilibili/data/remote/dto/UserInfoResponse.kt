package xyz.qiaosheng.bilibili.data.remote.dto

data class UserInfoResponse(
    val code: Int,
    val message: String? = null,
    val data: UserInfoData?
)

data class UserInfoData(
    val mid: Long,
    val name: String,
    val face: String,            // 头像 URL
    val sign: String,            // 个人签名
    val level: Int,              // 等级
    val sex: String,             // 性别
    val coins: Int,              // 硬币数
    val follower: Int,           // 粉丝数
    val following: Int           // 关注数
)
