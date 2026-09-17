package xyz.qiaosheng.bilibili.data.remote.api

import com.google.gson.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import xyz.qiaosheng.bilibili.data.remote.dto.*

/** 所有认证头来自一次账号快照；此接口的客户端必须禁用共享 CookieJar。 */
interface LibraryApiService {
    @GET("x/web-interface/history/cursor")
    suspend fun history(@Header("Cookie") cookie: String, @Query("max") max: Long = 0,
        @Query("view_at") viewAt: Long = 0, @Query("business") business: String = "",
        @Query("type") type: String = "archive", @Query("ps") pageSize: Int = 20): HistoryResponse
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun folders(@Header("Cookie") cookie: String, @Query("up_mid") accountId: Long,
        @Query("rid") aid: Long? = null, @Query("type") type: Int = 2): LibraryResponse<FavoriteFoldersData>
    @GET("x/v3/fav/resource/list")
    suspend fun favorites(@Header("Cookie") cookie: String, @Query("media_id") folderId: Long,
        @Query("pn") page: Int, @Query("ps") pageSize: Int = 20,
        @Query("order") order: String = "mtime", @Query("platform") platform: String = "web",
        @Query("type") type: Int = 0): LibraryResponse<FavoriteContentsData>
    // 该 Web 接口只提供最近点赞，不能用伪造页码声称覆盖账号的完整点赞历史。
    @GET("x/space/like/video")
    suspend fun recentLikes(@Header("Cookie") cookie: String, @Query("vmid") accountId: Long): LibraryResponse<JsonElement>
    @GET("x/web-interface/archive/has/like")
    suspend fun hasLike(@Header("Cookie") cookie: String, @Query("bvid") bvid: String): LibraryResponse<Int>
    @GET("x/web-interface/view")
    suspend fun detail(@Header("Cookie") cookie: String, @Query("bvid") bvid: String): LibraryResponse<LibraryVideoDto>
    @FormUrlEncoded @POST("x/web-interface/archive/like")
    suspend fun like(@Header("Cookie") cookie: String, @Field("bvid") bvid: String,
        @Field("like") like: Int, @Field("csrf") csrf: String): LibraryResponse<JsonElement>
    @FormUrlEncoded @POST("x/v3/fav/resource/deal")
    suspend fun favorite(@Header("Cookie") cookie: String, @Field("rid") aid: Long,
        @Field("add_media_ids") addIds: String, @Field("del_media_ids") deleteIds: String,
        @Field("csrf") csrf: String, @Field("type") type: Int = 2,
        @Field("platform") platform: String = "web"): LibraryResponse<JsonElement>
    @FormUrlEncoded @POST("x/v2/history/report")
    suspend fun report(@Header("Cookie") cookie: String, @Field("aid") aid: Long, @Field("cid") cid: Long,
        @Field("progress") progress: Long, @Field("csrf") csrf: String,
        @Field("platform") platform: String = "android"): LibraryResponse<JsonElement>
    @FormUrlEncoded @POST("x/v2/history/delete")
    suspend fun deleteHistory(@Header("Cookie") cookie: String, @Field("kid") key: String,
        @Field("csrf") csrf: String): LibraryResponse<JsonElement>
}
