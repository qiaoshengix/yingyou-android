package xyz.qiaosheng.bilibili.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import xyz.qiaosheng.bilibili.core.error.requireApiData
import xyz.qiaosheng.bilibili.data.local.dao.SearchHistoryDao
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity
import xyz.qiaosheng.bilibili.data.mapper.toUiModel
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.model.search.SearchResult

class SearchRepository @Inject constructor(
    val apiService: BiliApiService,
    val searchHistoryDao: SearchHistoryDao
) {

    fun getSearchHistory(): Flow<List<SearchHistoryEntity>> {
        return searchHistoryDao.getAll()
    }

    suspend fun search(query: String): List<SearchResult> {
        searchHistoryDao.insert(SearchHistoryEntity(query, System.currentTimeMillis()))
        val response = apiService.search(query)
        return requireApiData(response.code, response.message, response.data).result.orEmpty().mapNotNull {
            it.toUiModel()
        }
    }

    suspend fun clear() {
        searchHistoryDao.clearAll()
    }

    suspend fun delete(keyword: String) {
        searchHistoryDao.delete(keyword)
    }
}
