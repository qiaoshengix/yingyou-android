package xyz.qiaosheng.bilibili.ui.mine

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.qiaosheng.bilibili.ui.mine.components.MineContent

/** 个人页入口：协调页面状态、操作提示、主题入口和导航回调。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onOpenThemeSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToLikes: () -> Unit,
    onNavigateToOffline: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToServiceLab: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 操作提示展示结束后清除，避免状态重订阅时重复显示同一消息。
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    TextButton(onClick = onOpenThemeSettings) { Text("主题") }
                }
            )
        }
    ) { padding ->
        MineContent(
            state = uiState,
            contentPadding = padding,
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToFavorites = onNavigateToFavorites,
            onNavigateToLikes = onNavigateToLikes,
            onNavigateToOffline = onNavigateToOffline,
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToServiceLab = onNavigateToServiceLab,
            onRefreshProfile = viewModel::refresh,
            onLogout = viewModel::logout
        )
    }
}
