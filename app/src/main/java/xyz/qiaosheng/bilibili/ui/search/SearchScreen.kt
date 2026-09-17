package xyz.qiaosheng.bilibili.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.ui.search.components.ClearSearchHistoryDialog
import xyz.qiaosheng.bilibili.ui.search.components.SearchHistoryContent
import xyz.qiaosheng.bilibili.ui.search.components.SearchResultItem
import xyz.qiaosheng.bilibili.ui.search.components.SearchTopBar

/** 页面拥有键盘、确认弹窗与 Snackbar；历史/结果组件只接收状态和事件。 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistoryList.collectAsStateWithLifecycle()
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submitSearch: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.performSearch()
    }

    if (showClearConfirmation) {
        ClearSearchHistoryDialog(
            onConfirm = {
                showClearConfirmation = false
                viewModel.searchHistoryClear()
            },
            onDismiss = { showClearConfirmation = false }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.historyActionMessage) {
        uiState.historyActionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissHistoryActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SearchTopBar(
                query = uiState.query,
                isSearching = uiState.isSearching,
                onQueryChange = viewModel::onSearchValueChange,
                onSearch = submitSearch,
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            uiState.isSearching -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null -> ErrorContent(
                message = uiState.errorMessage!!,
                onRetry = viewModel::performSearch,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            uiState.hasSearched && uiState.results.isNotEmpty() -> {
                LazyColumn(Modifier.padding(padding)) {
                    items(uiState.results, key = { it.bvid }) { result ->
                        SearchResultItem(result, onClick = { onNavigateToVideo(result.bvid) })
                    }
                }
            }
            uiState.hasSearched -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("未找到相关视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> SearchHistoryContent(
                history = searchHistory,
                errorMessage = uiState.historyErrorMessage,
                onRetry = viewModel::retrySearchHistory,
                onSearch = { keyword ->
                    viewModel.onSearchValueChange(keyword)
                    submitSearch()
                },
                onDelete = viewModel::deleteSearchHistory,
                onClear = { showClearConfirmation = true },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}
