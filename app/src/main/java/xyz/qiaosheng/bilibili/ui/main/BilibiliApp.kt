package xyz.qiaosheng.bilibili.ui.main

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.qiaosheng.bilibili.core.ui.ErrorContent
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.ui.navigation.AppNavHost
import xyz.qiaosheng.bilibili.ui.settings.theme.ThemeSettingsDialog
import xyz.qiaosheng.bilibili.ui.settings.theme.ThemeViewModel
import xyz.qiaosheng.bilibili.ui.theme.BilibiliTheme
import xyz.qiaosheng.bilibili.ui.theme.SystemBarsEffect

/** Activity 级状态在这里汇合，主题设置与导航共享同一棵 Compose 树。 */
@Composable
fun BilibiliApp() {
    val viewModel: MainViewModel = hiltViewModel()
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = themeState.preferences.mode.isDark(isSystemInDarkTheme())
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }

    SystemBarsEffect(darkTheme)

    BilibiliTheme(darkTheme = darkTheme, themeColor = themeState.preferences.color) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = startupState) {
                is UiState.Success -> AppNavHost(
                    onOpenThemeSettings = { showThemeSettings = true }
                )
                is UiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = viewModel::initialize,
                    modifier = Modifier.fillMaxSize()
                )
                UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (showThemeSettings) {
            ThemeSettingsDialog(
                state = themeState,
                onModeSelected = themeViewModel::selectMode,
                onColorSelected = themeViewModel::selectColor,
                onRetryLoading = themeViewModel::retryLoading,
                onRetrySaving = themeViewModel::retrySaving,
                onDismiss = { showThemeSettings = false }
            )
        }
    }
}
