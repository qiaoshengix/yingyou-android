package xyz.qiaosheng.bilibili.ui.mine.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.qiaosheng.bilibili.ui.mine.MineUiState

/** 资料加载状态只影响顶部卡片，本地历史与缓存入口始终保留。 */
@Composable
internal fun MineContent(
    state: MineUiState,
    contentPadding: PaddingValues,
    onNavigateToHistory: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToOffline: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToServiceLab: () -> Unit,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            val statusModifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(16.dp)
            when (state) {
                MineUiState.Loading -> MineLoadingContent(statusModifier)
                MineUiState.LoggedOut -> MineLoggedOutContent(onNavigateToLogin, statusModifier)
                is MineUiState.Content -> UserProfileCard(state.userProfile)
                is MineUiState.Error -> MineErrorContent(state.message, onRefreshProfile, statusModifier)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            MineMenuRow(
                icon = Icons.Default.History,
                title = "历史记录",
                onClick = onNavigateToHistory
            )
            MineMenuRow(
                icon = Icons.Default.Star,
                title = "我的收藏",
                onClick = onNavigateToFavorites
            )
            MineMenuRow(
                icon = Icons.Default.ThumbUp,
                title = "我的点赞",
                onClick = onNavigateToLikes
            )
            MineMenuRow(
                icon = Icons.Default.Download,
                title = "离线缓存",
                onClick = onNavigateToOffline
            )
            MineMenuRow(
                icon = Icons.Default.Build,
                title = "Service 实验室",
                onClick = onNavigateToServiceLab
            )
        }
        if (state is MineUiState.Content || state is MineUiState.Error) {
            item {
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
