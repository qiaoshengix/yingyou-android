package xyz.qiaosheng.bilibili.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import xyz.qiaosheng.bilibili.core.error.ApiException
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.data.auth.AuthSessionManager
import xyz.qiaosheng.bilibili.data.remote.api.PassportApiService
import xyz.qiaosheng.bilibili.data.remote.dto.QrData
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.model.auth.LoginStatus

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: PassportApiService,
    private val cookieJar: BiliCookieJar,
    private val authSessionManager: AuthSessionManager
) {
    suspend fun generateQrCode(): QrData {
        val response = apiService.generateQrCode()
        return requireApiData(response.code, response.message, response.data)
    }

    suspend fun pollQrCode(qrcodeKey: String): LoginStatus {
        val response = apiService.pollQrCode(qrcodeKey)
        val data = requireApiData(response.code, response.message, response.data)
        return when (data.code) {
            0 -> if (establishSession()) LoginStatus.Success else LoginStatus.Failed
            86090 -> LoginStatus.Scanned
            86038 -> LoginStatus.Expired
            86101 -> LoginStatus.Waiting
            else -> throw ApiException(data.code, data.message)
        }
    }

    private suspend fun establishSession(): Boolean {
        val sessdata = cookieJar.getCookieValue("bilibili.com","SESSDATA")
        val biliJct = cookieJar.getCookieValue("bilibili.com","bili_jct")
        val dedeUserId = cookieJar.getCookieValue("bilibili.com","DedeUserID")

        if (sessdata.isNullOrBlank() || dedeUserId.isNullOrBlank()) return false

        authSessionManager.establishSession(
            sessdata = sessdata,
            biliJct = biliJct.orEmpty(),
            dedeUserId = dedeUserId
        )
        return true
    }
}
