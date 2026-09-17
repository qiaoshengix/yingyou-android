package xyz.qiaosheng.bilibili.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import xyz.qiaosheng.bilibili.data.remote.AuthDanmakuSessionSource
import xyz.qiaosheng.bilibili.data.remote.DanmakuRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.DanmakuSessionSource
import xyz.qiaosheng.bilibili.data.remote.RetrofitDanmakuRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.api.ApiConstants
import xyz.qiaosheng.bilibili.data.remote.api.DanmakuApiService
import xyz.qiaosheng.bilibili.data.remote.network.DanmakuBodyLimitInterceptor

@Module
@InstallIn(SingletonComponent::class)
object DanmakuModule {
    @Provides @Singleton
    fun api(client: OkHttpClient): DanmakuApiService = Retrofit.Builder()
        .baseUrl(ApiConstants.BASE_URL)
        // Use the captured account's Cookie and ignore late response cookies.
        .client(client.newBuilder().cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(DanmakuBodyLimitInterceptor()).build())
        .build().create(DanmakuApiService::class.java)

    @Provides @Singleton
    fun remote(value: RetrofitDanmakuRemoteDataSource): DanmakuRemoteDataSource = value

    @Provides @Singleton
    fun session(value: AuthDanmakuSessionSource): DanmakuSessionSource = value
}
