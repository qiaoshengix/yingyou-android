package xyz.qiaosheng.bilibili.ui.video.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import xyz.qiaosheng.bilibili.core.error.toUserMessage
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.model.video.Comment
import xyz.qiaosheng.bilibili.ui.video.VideoUiState
import xyz.qiaosheng.bilibili.ui.video.VideoActionsState

/** 视频信息和评论共用一个滚动容器，分页状态随评论列表展示。 */
@Composable
internal fun VideoDetailContent(
    state: VideoUiState,
    comments: LazyPagingItems<Comment>,
    actions: VideoActionsState,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item { VideoInfoSection(state.video) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
        item { VideoOwnerSection(state.video.owner) }
        item { VideoActionBar(state.video, actions, state.isOffline, onLike, onFavorite, onDownload, onShare) }
        if (actions.status.pendingCount > 0) {
            item { Text("${actions.status.pendingCount} 项操作等待同步", Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        actions.status.error?.let { message ->
            item { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
        if (state.isOffline) {
            item { Text("正在播放本地缓存 · 评论需联网查看", Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }
        item {
            Text(
                text = "评论 ${state.totalCommentCount}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall
            )
        }

        when (val refreshState = comments.loadState.refresh) {
            is LoadState.Loading -> if (comments.itemCount == 0) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }
            is LoadState.Error -> item {
                ErrorContent(refreshState.error.toUserMessage(), comments::retry, Modifier.fillMaxWidth())
            }
            is LoadState.NotLoading -> if (comments.itemCount == 0) {
                item { Text("还没有评论", Modifier.padding(16.dp)) }
            }
        }

        items(
            count = comments.itemCount,
            key = comments.itemKey { comments -> comments.id }
        ) { index ->
            val comment = comments[index]

            if (comment != null) {
                CommentItem(comment)
                HorizontalDivider(
                    modifier.padding(start = 58.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }

        when (val appendState = comments.loadState.append) {
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
                        onClick = { comments.retry() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${appendState.error.toUserMessage()}，点击重试")
                    }
                }
            }

            is LoadState.NotLoading -> {
                if (appendState.endOfPaginationReached && comments.itemCount > 0) {
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
