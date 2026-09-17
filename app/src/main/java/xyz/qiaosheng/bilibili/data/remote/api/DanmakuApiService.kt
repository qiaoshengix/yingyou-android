package xyz.qiaosheng.bilibili.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

interface DanmakuApiService {
    @Streaming
    @GET("x/v2/dm/wbi/web/seg.so")
    suspend fun segment(
        @Header("Cookie") cookie: String,
        @QueryMap parameters: Map<String, String>,
    ): ResponseBody
}
