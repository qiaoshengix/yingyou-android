package xyz.qiaosheng.bilibili.data.remote.dto

// 生成二维码的响应
data class QrGenerateResponse(
    val message: String,
    val ttl: Int,
    val code: Int,
    val data: QrData?
)

data class QrData(
    val url: String,           // 二维码内容 URL
    val qrcodeKey: String      // 由 Gson 自动映射服务器的 qrcode_key
)

// 轮询扫码状态的响应
data class QrPollResponse(
    val code: Int,             // 0=成功, 86101=未扫, 86090=已扫未确认, 86038=过期
    val message: String,
    val data: QrPollData?
)

data class QrPollData(
    val message: String,
    val code: Int,
    val timestamp: Long,
    val url: String?,          // 登录成功时有值（含 Cookie 信息）
    val refreshToken: String?  // 由 Gson 自动映射服务器的 refresh_token
)
