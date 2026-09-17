package xyz.qiaosheng.bilibili.data.remote.network

import okhttp3.Interceptor
import okhttp3.Response
import xyz.qiaosheng.bilibili.data.remote.api.ApiConstants

// 请求头拦截器
class BiliHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", ApiConstants.USER_AGENT)
            .header("Referer", ApiConstants.REFERER)
            .build()
        return chain.proceed(request)
    }
}
