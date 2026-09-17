package xyz.qiaosheng.bilibili.support

import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.qiaosheng.bilibili.data.local.dao.SearchHistoryDao
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity
import xyz.qiaosheng.bilibili.data.remote.dto.SearchData
import xyz.qiaosheng.bilibili.data.remote.dto.SearchItem
import xyz.qiaosheng.bilibili.data.remote.dto.SearchResponse

/** Retrofit 接口的内存替身，不建立真实网络连接。 */
inline fun <reified T : Any> fakeApi(
    crossinline response: suspend (String, List<Any?>) -> Any?
): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
    val args = arguments.orEmpty()
    @Suppress("UNCHECKED_CAST")
    val continuation = args.last() as Continuation<Any?>
    val call: suspend () -> Any? = { response(method.name, args.dropLast(1)) }
    call.startCoroutine(continuation)
    COROUTINE_SUSPENDED
} as T

class FakeSearchHistoryDao : SearchHistoryDao {
    val history = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
    var historyFlow: () -> Flow<List<SearchHistoryEntity>> = { history }
    var insertError: Exception? = null
    var clearError: Exception? = null
    var deleteError: Exception? = null
    var insertCount = 0
    override fun getAll() = historyFlow()
    override suspend fun insert(entity: SearchHistoryEntity) {
        insertCount++
        insertError?.let { throw it }
        history.value = listOf(entity)
    }
    override suspend fun delete(keyword: String) {
        deleteError?.let { throw it }
        history.value = history.value.filterNot { it.keyword == keyword }
    }
    override suspend fun insertOrReplace(entity: SearchHistoryEntity) = insert(entity)
    override suspend fun prune() = Unit
    override suspend fun clearAll() {
        clearError?.let { throw it }
        history.value = emptyList()
    }
}

fun searchResponse(bvid: String) = SearchResponse(code = 0, data = SearchData(
    result = listOf(SearchItem("video", bvid, "title", null, null, null, null, null, null, null, null)),
    page = 1, pagesize = 20
))
