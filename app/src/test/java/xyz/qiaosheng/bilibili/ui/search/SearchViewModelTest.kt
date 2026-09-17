package xyz.qiaosheng.bilibili.ui.search

import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.local.entity.SearchHistoryEntity
import xyz.qiaosheng.bilibili.data.remote.api.BiliApiService
import xyz.qiaosheng.bilibili.data.remote.dto.SearchResponse
import xyz.qiaosheng.bilibili.data.repository.SearchRepository
import xyz.qiaosheng.bilibili.support.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dao = FakeSearchHistoryDao()
    private val logged = mutableListOf<Throwable>()
    private lateinit var vm: SearchViewModel
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }
    private fun create(response: suspend (String, List<Any?>) -> Any?) {
        vm = SearchViewModel(SearchRepository(fakeApi<BiliApiService>(response), dao),
            ErrorReporter { _, error -> logged += error })
    }

    @Test fun failureEndsLoadingAndRetryCanSucceed() = runTest {
        var attempts = 0
        create { _, _ -> if (++attempts == 1) throw SocketTimeoutException() else searchResponse("new") }
        vm.onSearchValueChange("query")
        vm.performSearch()
        runCurrent()
        assertFalse(vm.uiState.value.isSearching)
        assertFalse(vm.uiState.value.hasSearched)
        assertTrue(vm.uiState.value.errorMessage!!.contains("超时"))
        assertTrue(logged.single() is SocketTimeoutException)
        vm.performSearch()
        runCurrent()
        assertNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.hasSearched)
        assertEquals("new", vm.uiState.value.results.single().bvid)
        assertEquals(2, attempts)
    }

    @Test fun businessFailureIsNotShownAsNoResults() = runTest {
        create { _, _ -> SearchResponse(code = -1, data = null) }
        vm.onSearchValueChange("query"); vm.performSearch(); runCurrent()
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.hasSearched)
    }

    @Test fun oldCancelledRequestCannotEndNewLoadingOrOverwriteResults() = runTest {
        create { _, args ->
            val query = args[0] as String
            if (query == "old") withContext(NonCancellable) { delay(1_000) }
            else delay(2_000)
            searchResponse(query)
        }
        vm.onSearchValueChange("old"); vm.performSearch(); runCurrent()
        vm.onSearchValueChange("new"); vm.performSearch(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertTrue(vm.uiState.value.isSearching)
        assertNull(vm.uiState.value.errorMessage)
        advanceUntilIdle()
        assertEquals("new", vm.uiState.value.results.single().bvid)
        assertTrue(logged.isEmpty())
    }

    @Test fun clearingQueryCancelsSearchWithoutDisplayingFailure() = runTest {
        create { _, _ -> delay(1_000); searchResponse("old") }
        vm.onSearchValueChange("old"); vm.performSearch(); runCurrent()
        vm.onSearchValueChange(""); advanceUntilIdle()
        assertFalse(vm.uiState.value.isSearching)
        assertFalse(vm.uiState.value.hasSearched)
        assertNull(vm.uiState.value.errorMessage)
        assertTrue(logged.isEmpty())
    }

    @Test fun blankQueryDoesNotWriteHistoryOrRequestNetwork() = runTest {
        create { _, _ -> error("Must not request") }
        vm.onSearchValueChange("   "); vm.performSearch(); runCurrent()
        assertEquals(0, dao.insertCount)
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test fun historyFlowFailureCanBeResubscribed() = runTest {
        var subscriptions = 0
        dao.historyFlow = { flow {
            if (++subscriptions == 1) throw IOException("disk")
            emit(emptyList())
        } }
        create { _, _ -> searchResponse("unused") }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.searchHistoryList.collect() }
        runCurrent()
        assertNotNull(vm.uiState.value.historyErrorMessage)
        vm.retrySearchHistory(); runCurrent()
        assertNull(vm.uiState.value.historyErrorMessage)
        assertEquals(2, subscriptions)
    }

    @Test fun clearFailureKeepsSearchContentAndExposesActionMessage() = runTest {
        create { _, _ -> searchResponse("existing") }
        vm.onSearchValueChange("query"); vm.performSearch(); runCurrent()
        dao.clearError = IOException("disk")
        vm.searchHistoryClear(); runCurrent()
        assertEquals("existing", vm.uiState.value.results.single().bvid)
        assertNotNull(vm.uiState.value.historyActionMessage)
        assertEquals(1, dao.history.value.size)
    }

    @Test fun historyWriteFailureEndsSearchLoading() = runTest {
        dao.insertError = IOException("disk")
        create { _, _ -> error("Must not request after database failure") }
        vm.onSearchValueChange("query"); vm.performSearch(); runCurrent()
        assertFalse(vm.uiState.value.isSearching)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test fun deletingHistoryRemovesOnlySelectedWordWithoutSearching() = runTest {
        dao.history.value = listOf(SearchHistoryEntity("first", 1L), SearchHistoryEntity("second", 2L))
        create { _, _ -> error("Deletion must not request network") }
        vm.onSearchValueChange("current input")
        vm.deleteSearchHistory("first"); runCurrent()
        assertEquals(listOf("second"), dao.history.value.map { it.keyword })
        assertEquals("current input", vm.uiState.value.query)
        assertFalse(vm.uiState.value.isSearching)
        assertEquals(0, dao.insertCount)
    }

    @Test fun failedDeletionKeepsHistoryAndCanBeRetried() = runTest {
        dao.history.value = listOf(SearchHistoryEntity("first", 1L))
        dao.deleteError = IOException("disk")
        create { _, _ -> error("Deletion must not request network") }
        vm.deleteSearchHistory("first"); runCurrent()
        assertEquals(1, dao.history.value.size)
        assertNotNull(vm.uiState.value.historyActionMessage)
        dao.deleteError = null
        vm.deleteSearchHistory("first"); runCurrent()
        assertTrue(dao.history.value.isEmpty())
        assertNull(vm.uiState.value.historyActionMessage)
    }

    @Test fun searchUsesAndDisplaysTrimmedKeyword() = runTest {
        var requestedQuery: String? = null
        create { _, args -> requestedQuery = args[0] as String; searchResponse("result") }
        vm.onSearchValueChange("  Kotlin  "); vm.performSearch(); runCurrent()
        assertEquals("Kotlin", requestedQuery)
        assertEquals("Kotlin", vm.uiState.value.query)
        assertEquals("Kotlin", dao.history.value.single().keyword)
    }
}
