package xyz.qiaosheng.bilibili.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query
import xyz.qiaosheng.bilibili.data.remote.dto.QrGenerateResponse
import xyz.qiaosheng.bilibili.data.remote.dto.QrPollResponse

interface PassportApiService {

    // 生成登录二维码
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrGenerateResponse

    // 轮询二维码登录状态
    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(
        @Query("qrcode_key") qrcodeKey: String
    ): QrPollResponse
}
