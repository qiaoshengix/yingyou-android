package xyz.qiaosheng.bilibili.ui.search

import xyz.qiaosheng.bilibili.model.search.SearchResult

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val historyErrorMessage: String? = null,
    val historyActionMessage: String? = null,
)
