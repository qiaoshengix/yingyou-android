package xyz.qiaosheng.bilibili.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.ui.library.components.FavoriteFolderPicker
import xyz.qiaosheng.bilibili.ui.library.components.LibraryEmptyContent
import xyz.qiaosheng.bilibili.ui.library.components.LibraryStatusContent
import xyz.qiaosheng.bilibili.ui.library.components.LibraryVideoCard

/** 历史、收藏与点赞共用列表编排，数据缓存和同步规则由仓库统一维护。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    kind: LibraryKind,
    onBack: () -> Unit,
    onNavigateToVideo: (LibraryVideo) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot.takeIf { state.kind == kind }
    val error = state.requestError ?: snapshot?.error
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 删除确认只属于当前账号，切换账号后不能提交旧账号的对话框。
    var itemToDelete by remember(kind, snapshot?.accountId) { mutableStateOf<LibraryVideo?>(null) }

    LaunchedEffect(kind) { viewModel.open(kind) }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissActionMessage()
        }
    }
    LaunchedEffect(kind, snapshot?.accountId, snapshot?.selectedFolderId) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(kind, snapshot?.accountId, snapshot?.selectedFolderId, snapshot?.items?.size, snapshot?.hasMore) {
        if (snapshot?.hasMore != true) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastIndex >= layout.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            // ViewModel 会合并重复请求，追加失败后等待用户明确重试。
            if (nearEnd) viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = snapshot?.loading != true) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新${kind.title}")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = snapshot?.loading == true && snapshot.items.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (kind == LibraryKind.FAVORITES && !snapshot?.folders.isNullOrEmpty()) {
                    item(key = "folders") {
                        FavoriteFolderPicker(requireNotNull(snapshot), viewModel::selectFolder)
                    }
                }
                if (snapshot != null) {
                    item(key = "status") {
                        LibraryStatusContent(
                            kind = kind,
                            snapshot = snapshot,
                            error = error,
                            syncing = state.operationInProgress,
                            onLogin = onNavigateToLogin,
                            onRefresh = viewModel::refresh,
                            onSync = viewModel::sync
                        )
                    }
                }
                if (snapshot?.items.isNullOrEmpty()) {
                    item(key = "empty") {
                        LibraryEmptyContent(
                            kind = kind,
                            loading = state.initialLoading || snapshot?.loading == true,
                            loginRequired = snapshot?.loginRequired == true,
                            error = error,
                            onLogin = onNavigateToLogin,
                            onRetry = viewModel::refresh
                        )
                    }
                } else {
                    items(requireNotNull(snapshot).items, key = { it.bvid }) { video ->
                        LibraryVideoCard(
                            video = video,
                            kind = kind,
                            operationInProgress = state.operationInProgress,
                            onOpen = onNavigateToVideo,
                            onRemove = {
                                if (kind == LibraryKind.HISTORY) itemToDelete = it
                                else viewModel.remove(it, snapshot.accountId)
                            }
                        )
                    }
                }
                // 历史页过滤掉直播/文章后可能暂时没有视频，仍须保留下一页及失败重试入口。
                if (snapshot != null && (snapshot.items.isNotEmpty() || snapshot.hasMore)) {
                    item(key = "footer") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                snapshot.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                                snapshot.hasMore -> TextButton(onClick = { viewModel.loadMore(retry = true) }) {
                                    Text(if (error != null) "加载失败，点击重试" else "加载更多")
                                }
                                !snapshot.loading -> Text("已显示全部已保存内容")
                            }
                        }
                    }
                }
            }
        }
    }

    itemToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("删除观看记录") },
            text = {
                Text("确定删除「${video.title.ifBlank { video.bvid }}」的观看记录？" +
                    if (snapshot?.accountId != null) "删除操作会同步到当前账号。" else "")
            },
            confirmButton = {
                TextButton(onClick = {
                    itemToDelete = null
                    viewModel.remove(video, snapshot?.accountId)
                }, enabled = !state.operationInProgress) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }
}
