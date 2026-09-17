package xyz.qiaosheng.bilibili.ui.dynamic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.model.dynamic.DynamicFeedType
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicAppendError
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicAppendLoading
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicEmptyContent
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicInitialError
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicInitialLoading
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicLoggedOutContent
import xyz.qiaosheng.bilibili.ui.dynamic.components.DynamicPostCard

/** 动态页面入口：收集登录、分类和分页状态，并协调刷新、重试与导航。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: DynamicViewModel = hiltViewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val selectedFeedType by viewModel.selectedFeedType.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    // 首次加载占满页面；刷新已有内容时保留列表，仅显示下拉刷新状态。
    val isInitialLoading = posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0
    val isRefreshing = posts.loadState.refresh is LoadState.Loading && posts.itemCount > 0

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("动态") },
                    actions = {
                        if (isLoggedIn) {
                            IconButton(onClick = posts::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新动态")
                            }
                        }
                    }
                )
                if (isLoggedIn) {
                    PrimaryTabRow(selectedTabIndex = selectedFeedType.ordinal) {
                        DynamicFeedType.entries.forEach { feedType ->
                            Tab(
                                selected = selectedFeedType == feedType,
                                onClick = { viewModel.selectFeedType(feedType) },
                                text = { Text(feedType.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (!isLoggedIn) {
            DynamicLoggedOutContent(
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = posts::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isInitialLoading -> DynamicInitialLoading()
                posts.loadState.refresh is LoadState.Error && posts.itemCount == 0 -> {
                    DynamicInitialError(
                        message = (posts.loadState.refresh as LoadState.Error).error.toUserMessage(),
                        onRetry = posts::retry
                    )
                }
                posts.loadState.refresh is LoadState.NotLoading && posts.itemCount == 0 -> {
                    DynamicEmptyContent()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // 刷新失败时保留缓存列表，错误提示插入列表顶部以便重试。
                        val refreshError = posts.loadState.refresh as? LoadState.Error
                        if (refreshError != null) {
                            item {
                                ErrorContent(
                                    message = refreshError.error.toUserMessage(),
                                    onRetry = posts::retry,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        items(
                            count = posts.itemCount,
                            key = { index -> posts[index]?.id ?: "placeholder-$index" }
                        ) { index ->
                            posts[index]?.let { post ->
                                DynamicPostCard(
                                    post = post,
                                    onNavigateToVideo = onNavigateToVideo
                                )
                            }
                        }

                        when (val appendState = posts.loadState.append) {
                            is LoadState.Loading -> item { DynamicAppendLoading() }
                            is LoadState.Error -> item {
                                DynamicAppendError(
                                    message = appendState.error.toUserMessage(),
                                    onRetry = posts::retry
                                )
                            }
                            is LoadState.NotLoading -> Unit
                        }
                    }
                }
            }
        }
    }
}
