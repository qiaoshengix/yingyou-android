package xyz.qiaosheng.bilibili.data.remote.dto

data class NavResponse(
    val code: Int,
    val message: String?,
    val data: NavData?
)

data class NavData(
    val wbiImg: WbiImage?
)

data class WbiImage(
    val imgUrl: String,
    val subUrl: String
)
