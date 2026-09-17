package xyz.qiaosheng.bilibili.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.ui.home.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToVideo: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val videos = viewModel.videos.collectAsLazyPagingItems()

    val isInitialLoading =
        videos.loadState.refresh is LoadState.Loading && videos.itemCount == 0

    val isRefreshing =
        videos.loadState.refresh is LoadState.Loading && videos.itemCount > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("首页")
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSearch
                    ) {
                        Icon(Icons.Default.Search,"搜索")
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = videos::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                when (val refreshState = videos.loadState.refresh) {
                    is LoadState.Loading -> {
                        if (isInitialLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(innerPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }

                    is LoadState.Error -> {
                        if (videos.itemCount == 0) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(innerPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = videos::retry) {
                                        Text("${refreshState.error.toUserMessage()}，点击重试")
                                    }
                                }
                            }
                        } else {
                            item {
                                ErrorContent(refreshState.error.toUserMessage(), videos::retry, Modifier.fillMaxWidth())
                            }
                        }
                    }

                    is LoadState.NotLoading -> Unit
                }
                items(
                    count = videos.itemCount,
                    key = videos.itemKey { videos -> videos.bvid }
                ) { index ->
                    val video = videos[index]

                    if (video != null) {
                        VideoCard(
                            video = video,
                            onClick = { onNavigateToVideo(video.bvid) }
                        )
                    }
                }
                when (val appendState = videos.loadState.append) {
                    is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    is LoadState.Error -> {
                        item {
                            TextButton(
                                onClick = { videos.retry() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${appendState.error.toUserMessage()}，点击重试")
                            }
                        }
                    }

                    is LoadState.NotLoading -> {
                        if (appendState.endOfPaginationReached && videos.itemCount > 0) {
                            item {
                                Text(
                                    text = "已经到底了",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
