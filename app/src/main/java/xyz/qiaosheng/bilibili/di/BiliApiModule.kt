package xyz.qiaosheng.bilibili.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import xyz.qiaosheng.bilibili.data.remote.api.ApiConstants
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.api.CommentApiService
import xyz.qiaosheng.bilibili.data.remote.api.PassportApiService
import xyz.qiaosheng.bilibili.data.remote.network.BiliCookieJar
import xyz.qiaosheng.bilibili.data.remote.network.BiliHeaderInterceptor

@Module
@InstallIn(SingletonComponent::class)
class BiliApiModule {

    @Provides
    @Singleton
    fun biliCookieJarProvide(): BiliCookieJar {
        return BiliCookieJar()
    }

    @Provides
    @Singleton
    fun gsonProvide(): Gson {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }

    @Provides
    @Singleton
    fun okHttpClientProvide(
        biliCookieJar: BiliCookieJar
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(biliCookieJar)
            .addInterceptor(BiliHeaderInterceptor())     // 请求头
            .addInterceptor(HttpLoggingInterceptor().apply {
//                level = HttpLoggingInterceptor.Level.BASIC  // 日志（Release 时关闭）
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun biliApiServiceProvide(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): BiliApiService {
        return createRetrofit(ApiConstants.BASE_URL, okHttpClient, gson)
            .create(BiliApiService::class.java)
    }

    @Provides
    @Singleton
    fun passportApiServiceProvide(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): PassportApiService {
        return createRetrofit(ApiConstants.PASSPORT_BASE_URL, okHttpClient, gson)
            .create(PassportApiService::class.java)
    }

    @Provides
    @Singleton
    fun commentApiServiceProvide(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): CommentApiService {
        return createRetrofit(ApiConstants.COMMENT_URL, okHttpClient, gson)
            .create(CommentApiService::class.java)
    }

    private fun createRetrofit(
        baseUrl: String,
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun mediaDataSourceFactoryProvide(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): DataSource.Factory {
        val httpDataSourceFactory =
            OkHttpDataSource.Factory(okHttpClient)

        return DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )
    }
}
