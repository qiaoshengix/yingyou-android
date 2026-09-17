package xyz.qiaosheng.bilibili.data.remote.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import xyz.qiaosheng.bilibili.data.remote.dto.AddCommentResponse
import xyz.qiaosheng.bilibili.data.remote.dto.ApiResponse
import xyz.qiaosheng.bilibili.data.remote.dto.CommentResponse
import xyz.qiaosheng.bilibili.data.remote.dto.DynamicResponse
import xyz.qiaosheng.bilibili.data.remote.dto.HistoryResponse
import xyz.qiaosheng.bilibili.data.remote.dto.NavResponse
import xyz.qiaosheng.bilibili.data.remote.dto.PlayUrlResponse
import xyz.qiaosheng.bilibili.data.remote.dto.RecommendResponse
import xyz.qiaosheng.bilibili.data.remote.dto.RelationStatData
import xyz.qiaosheng.bilibili.data.remote.dto.SearchResponse
import xyz.qiaosheng.bilibili.data.remote.dto.UserInfoResponse
import xyz.qiaosheng.bilibili.data.remote.dto.VideoDetailResponse

/** B 站业务接口定义；只声明传输契约，响应转换和分页编排由数据仓库负责。 */
interface BiliApiService {

    // ========== 关注动态 ==========
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamics(
        @Query("type") type: String = "all",
        @Query("offset") offset: String? = null,
        @Query("timezone_offset") timezoneOffset: Int = -480
    ): DynamicResponse

    // ========== 首页推荐 ==========
    @GET("x/web-interface/index/top/feed/rcmd")
    suspend fun getRecommendVideos(
        @Query("fresh_type") freshType: Int = 4,
        @Query("ps") ps: Int = 30,          // 每页数量
        @Query("fresh_idx") freshIdx: Int = 1,
        @Query("fresh_idx_1h") freshIdx1h: Int = 1
    ): RecommendResponse

    // ========== 视频详情 ==========
    @GET("x/web-interface/view")
    suspend fun getVideoDetail(
        @Query("bvid") bvid: String
    ): VideoDetailResponse

    // ========== 播放地址 ==========
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") quality: Int = 127,
        @Query("fnval") fnval: Int = 16,  // 16 = DASH 格式
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1    // 允许 4K
    ): PlayUrlResponse

    // ========== 评论 ===========
    @GET("x/v2/reply/main")
    suspend fun getVideoComments(
        @Query("oid") aid: Long,
        @Query("type") type: Int = 1,
        @Query("mode") mode: Int = 3,
        @Query("next") next: Int = 0,
        @Query("ps") pageSize: Int = 20
    ): CommentResponse

    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun addVideoComment(
        @Field("oid") aid: Long,
        @Field("message") message: String,
        @Field("csrf") csrf: String,
        @Field("csrf_token") csrfToken: String,
        @Field("type") type: Int = 1
    ): AddCommentResponse

    // ========== UP 主统计 ===========
    @GET("x/relation/stat")
    suspend fun getRelationStat(
        @Query("vmid") mid: Long
    ): ApiResponse<RelationStatData>

    // 获取 WBI 动态密钥
    @GET("x/web-interface/nav")
    suspend fun getNav(): NavResponse

    // 获取当前用户信息（调用方需传入 WBI 签名参数）
    @GET("x/space/wbi/acc/info")
    suspend fun getUserInfo(
        @QueryMap parameters: Map<String, String>
    ): UserInfoResponse

    // 获取观看历史
    @GET("x/web-interface/history/cursor")
    suspend fun getHistory(
        @Query("max") max: Long = 0,        // 0=第一页
        @Query("view_at") viewAt: Long = 0,
        @Query("business") business: String = "archive",
        @Query("ps") ps: Int = 20
    ): HistoryResponse

    // 搜索视频
    @GET("x/web-interface/wbi/search/type")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("search_type") searchType: String = "video",  // video/user/live
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): SearchResponse

}
