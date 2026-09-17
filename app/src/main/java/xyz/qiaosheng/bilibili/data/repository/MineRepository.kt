package xyz.qiaosheng.bilibili.data.repository

import javax.inject.Inject
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.data.mapper.toUiModel
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.network.WbiSigner
import xyz.qiaosheng.bilibili.model.profile.UserProfile

class MineRepository @Inject constructor(
    private val apiService: BiliApiService,
    private val wbiSigner: WbiSigner
) {

    suspend fun getUserInfo(mid: Long): UserProfile {
        val signedParameters = wbiSigner.sign(mapOf("mid" to mid.toString()))
        val response = apiService.getUserInfo(signedParameters)
        val data = requireApiData(response.code, response.message, response.data)
        return data.toUiModel()
    }
}
