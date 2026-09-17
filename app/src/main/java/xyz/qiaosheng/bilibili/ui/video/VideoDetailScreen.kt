package xyz.qiaosheng.bilibili.ui.video

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import xyz.qiaosheng.bilibili.core.ui.rememberDownloadPermissionAction
import xyz.qiaosheng.bilibili.model.video.VideoPageData
import xyz.qiaosheng.bilibili.ui.video.components.FavoriteFoldersDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavBackStackEntry
import androidx.paging.compose.collectAsLazyPagingItems
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.ui.video.components.CommentInputBar
import xyz.qiaosheng.bilibili.ui.video.components.VideoDetailContent
import xyz.qiaosheng.bilibili.ui.video.components.VideoLoadError
import xyz.qiaosheng.bilibili.ui.video.player.ImmersiveFullscreenEffect
import xyz.qiaosheng.bilibili.ui.video.player.VideoPlayer
import xyz.qiaosheng.bilibili.ui.video.player.VideoPlayerLoading

/** 页面入口：收集状态、处理返回与评论反馈，并组装播放器和详情组件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    backStackEntry: NavBackStackEntry,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel(backStackEntry)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    val comments = viewModel.comments.collectAsLazyPagingItems()

    val player by viewModel.player.collectAsStateWithLifecycle()
    val danmakuViewModel: DanmakuViewModel = hiltViewModel(backStackEntry)
    val danmaku by danmakuViewModel.state.collectAsStateWithLifecycle()
    val actionsViewModel: VideoActionsViewModel = hiltViewModel(backStackEntry)
    val actions by actionsViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ImmersiveFullscreenEffect(enable = isFullScreen)

    val content = (uiState as? UiState.Success)?.data
    // 绑定独立于普通/全屏布局，切换画面不重建分段请求或覆盖用户开关。
    LaunchedEffect(player, content?.aid, content?.cid, content?.video?.duration, content?.isOffline, content?.isAudioOnly) {
        danmakuViewModel.bind(
            player = player,
            aid = content?.aid ?: 0L,
            cid = content?.cid ?: 0L,
            durationSeconds = content?.video?.duration?.toLong() ?: 0L,
            isOffline = content?.isOffline == true,
            isAudioOnly = content?.isAudioOnly == true,
        )
    }
    LifecycleStartEffect(danmakuViewModel) {
        danmakuViewModel.setActive(true)
        onStopOrDispose { danmakuViewModel.setActive(false) }
    }
    DisposableEffect(danmakuViewModel) {
        onDispose { danmakuViewModel.close() }
    }
    val showPageChrome = !isFullScreen
    LaunchedEffect(content?.video?.bvid, content?.cid) {
        content?.let { actionsViewModel.bind(VideoPageData(it.aid, it.cid, it.video)) }
    }
    LaunchedEffect(actions.message) {
        actions.message?.let {
            snackbarHostState.showSnackbar(it)
            actionsViewModel.dismissMessage()
        }
    }
    val requestDownload = rememberDownloadPermissionAction {
        content?.let { actionsViewModel.download(it.selectedQualityId) }
    }
    if (actions.showFolders) FavoriteFoldersDialog(
        state = actions, onToggle = actionsViewModel::toggleFolder,
        onRetry = actionsViewModel::openFolders, onDismiss = actionsViewModel::closeFolders
    )

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    LaunchedEffect(content?.commentMessage) {
        content?.commentMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeCommentMessage()
        }
    }

    LaunchedEffect(content?.commentsRefreshVersion) {
        if ((content?.commentsRefreshVersion ?: 0) > 0) comments.refresh()
    }

    LifecycleStartEffect(viewModel) {
        onStopOrDispose {
            viewModel.pausePlayback()
        }
    }

    if (isFullScreen && content != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayer(
                player = player,
                qualities = content.qualities,
                selectedQualityId = content.selectedQualityId,
                isFullScreen = true,
                onQualitySelected = viewModel::selectQuality,
                onFullScreenChange = { isFullScreen = it },
                isAudioOnly = content.isAudioOnly,
                danmaku = danmaku,
                onToggleBackgroundAudio = viewModel::toggleBackgroundAudio,
                onDanmakuEnabledChange = danmakuViewModel::setEnabled,
                onRetryDanmaku = danmakuViewModel::retry,
                modifier = Modifier.fillMaxSize()
            )
            content.playbackErrorMessage?.let { message ->
                ErrorContent(
                    message = message,
                    onRetry = viewModel::retryPlayback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            if (showPageChrome) {
                TopAppBar(
                    title = { Text("视频详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showPageChrome && content != null && !content.isOffline) {
                CommentInputBar(
                    value = content.commentDraft,
                    submitting = content.commentSubmitting,
                    onValueChange = viewModel::updateCommentDraft,
                    onSend = viewModel::submitComment
                )
            }
        },
        containerColor = if (isFullScreen) Color.Black else MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                UiState.Loading -> {
                    VideoPlayerLoading()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在加载视频详情…")
                    }
                }

                is UiState.Error -> {
                    VideoLoadError(
                        message = state.message,
                        onRetry = viewModel::loadVideo
                    )
                }

                is UiState.Success -> {
                    val data = state.data
                    VideoPlayer(
                        player = player,
                        qualities = data.qualities,
                        selectedQualityId = data.selectedQualityId,
                        isFullScreen = isFullScreen,
                        onQualitySelected = viewModel::selectQuality,
                        onFullScreenChange = { isFullScreen = it },
                        isAudioOnly = data.isAudioOnly,
                        danmaku = danmaku,
                        onToggleBackgroundAudio = viewModel::toggleBackgroundAudio,
                        onDanmakuEnabledChange = danmakuViewModel::setEnabled,
                        onRetryDanmaku = danmakuViewModel::retry,
                        modifier = if (isFullScreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        }
                    )
                    data.playbackErrorMessage?.let { message ->
                        ErrorContent(message, viewModel::retryPlayback, Modifier.fillMaxWidth())
                    }
                    if (showPageChrome) {
                        VideoDetailContent(
                            state = data,
                            comments = comments,
                            actions = actions,
                            onLike = {
                                if (actions.status.accountId == null) onNavigateToLogin()
                                else actionsViewModel.toggleLike()
                            },
                            onFavorite = {
                                if (actions.status.accountId == null) onNavigateToLogin()
                                else actionsViewModel.openFolders()
                            },
                            onDownload = requestDownload,
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${data.video.title}\nhttps://www.bilibili.com/video/${data.video.bvid}")
                                }
                                context.startActivity(Intent.createChooser(intent, "分享视频"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
