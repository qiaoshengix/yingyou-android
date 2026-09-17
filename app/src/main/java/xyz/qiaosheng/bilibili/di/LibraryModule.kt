package xyz.qiaosheng.bilibili.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import xyz.qiaosheng.bilibili.data.local.database.BiliDatabase
import xyz.qiaosheng.bilibili.data.remote.LibraryRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.RetrofitLibraryRemoteDataSource
import xyz.qiaosheng.bilibili.data.remote.api.ApiConstants
import xyz.qiaosheng.bilibili.data.remote.api.LibraryApiService
import xyz.qiaosheng.bilibili.data.sync.*

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides @Singleton @LibraryScope
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Provides fun dao(database: BiliDatabase) = database.libraryDao()
    @Provides @Singleton fun session(value: AuthLibrarySession): LibrarySession = value
    @Provides @Singleton fun scheduler(value: WorkManagerLibraryScheduler): LibrarySyncScheduler = value
    @Provides @Singleton fun remote(value: RetrofitLibraryRemoteDataSource): LibraryRemoteDataSource = value

    @Provides @Singleton
    fun api(client: OkHttpClient, gson: Gson): LibraryApiService = Retrofit.Builder()
        .baseUrl(ApiConstants.BASE_URL)
        // 显式 Cookie Header 来自请求创建时的账号快照；OkHttp 桥接层不得从共享 CookieJar 改写它。
        .client(client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build().create(LibraryApiService::class.java)
}
