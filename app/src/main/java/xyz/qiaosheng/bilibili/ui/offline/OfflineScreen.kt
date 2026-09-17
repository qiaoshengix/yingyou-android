package xyz.qiaosheng.bilibili.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.util.Locale
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.model.offline.OfflineStatus
import xyz.qiaosheng.bilibili.model.offline.OfflineVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (OfflineVideo) -> Unit,
    viewModel: OfflineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deletingId by remember { mutableStateOf<String?>(null) }
    val downloadAction = rememberOfflineDownloadAction(viewModel::notificationDenied)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    state.downloads.firstOrNull { it.id == deletingId }?.let { video ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("删除离线缓存？") },
            text = { Text("将删除「${video.title}」的音视频文件。") },
            confirmButton = { TextButton(onClick = { deletingId = null; viewModel.remove(video.id) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("离线缓存") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("仅 Wi-Fi 下载", style = MaterialTheme.typography.titleSmall)
                        Text("关闭后允许使用移动网络", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.wifiOnly, onCheckedChange = viewModel::setWifiOnly,
                        enabled = "network-setting" !in state.busyIds)
                }
            }
            state.loadError?.let { message -> item { ErrorContent(message, viewModel::retryLoading) } }
            if (state.isLoading) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
            else if (state.downloads.isEmpty() && state.loadError == null) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无离线缓存", style = MaterialTheme.typography.titleMedium)
                    Text("在视频详情页选择缓存，完成后即可断网播放。", modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.downloads, key = { it.id }) { video ->
                OfflineVideoCard(video, state.wifiOnly, video.id in state.busyIds,
                    onPlay = { onNavigateToVideo(video) },
                    onPause = { viewModel.pause(video.id) },
                    onResume = { downloadAction { viewModel.resume(video.id) } },
                    onRetry = { downloadAction { viewModel.retry(video.id) } },
                    onRemove = { deletingId = video.id })
            }
        }
    }
}

@Composable
private fun OfflineVideoCard(video: OfflineVideo, wifiOnly: Boolean, busy: Boolean,
    onPlay: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(video.coverUrl, contentDescription = null, modifier = Modifier.size(96.dp, 60.dp), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("${video.ownerName} · ${video.qualityLabel}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val status = when (video.status) {
                OfflineStatus.QUEUED -> "等待下载"
                OfflineStatus.DOWNLOADING -> "正在缓存"
                OfflineStatus.PAUSED -> "已暂停"
                OfflineStatus.WAITING_FOR_NETWORK -> if (wifiOnly) "等待 Wi-Fi" else "等待网络"
                OfflineStatus.COMPLETED -> "已完成 · 可离线播放"
                OfflineStatus.FAILED -> "缓存失败"
                OfflineStatus.REMOVING -> "正在删除"
            }
            Text(status, style = MaterialTheme.typography.labelMedium)
            if (video.status != OfflineStatus.COMPLETED && video.status != OfflineStatus.REMOVING) {
                video.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                Text(buildString {
                    append(formatOfflineBytes(video.downloadedBytes))
                    if (video.totalBytes > 0) append(" / ${formatOfflineBytes(video.totalBytes)}")
                }, style = MaterialTheme.typography.bodySmall)
            }
            video.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (video.status) {
                    OfflineStatus.COMPLETED -> Button(onClick = onPlay, enabled = !busy) { Text("本地播放") }
                    OfflineStatus.FAILED -> Button(onClick = onRetry, enabled = !busy) { Text("重试") }
                    OfflineStatus.PAUSED -> Button(onClick = onResume, enabled = !busy) { Text("继续") }
                    OfflineStatus.REMOVING -> Unit
                    else -> TextButton(onClick = onPause, enabled = !busy) { Text("暂停") }
                }
                TextButton(onClick = onRemove, enabled = !busy && video.status != OfflineStatus.REMOVING) { Text("删除") }
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

private fun formatOfflineBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
}
