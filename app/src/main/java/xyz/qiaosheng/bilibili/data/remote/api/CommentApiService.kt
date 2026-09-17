package xyz.qiaosheng.bilibili.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface CommentApiService {

    @GET("{cid}.xml")
    @Headers("Referer: https://www.bilibili.com")
    suspend fun getDanmakuXml(
        @Path("cid") cid: Long
    ): ResponseBody
}
