package xyz.qiaosheng.bilibili.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.qiaosheng.bilibili.core.ui.UiState
import xyz.qiaosheng.bilibili.model.auth.LoginStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loginSucceeded =
        (uiState as? UiState.Success)?.data?.loginStatus == LoginStatus.Success

    LaunchedEffect(loginSucceeded) {
        if (loginSucceeded) {
            onLoginSuccess()
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "B 站账号登录",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "请使用 B 站 App 扫描下方二维码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 二维码
            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    UiState.Loading -> {
                        CircularProgressIndicator()
                    }

                    is UiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = viewModel::loadQrCode) {
                                Text("重试")
                            }
                        }
                    }

                    is UiState.Success -> {
                        when (state.data.loginStatus) {
                            LoginStatus.Expired,
                            LoginStatus.Failed -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (state.data.loginStatus == LoginStatus.Expired) {
                                            "二维码已过期"
                                        } else {
                                            "登录失败"
                                        },
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = viewModel::loadQrCode) {
                                        Text("刷新二维码")
                                    }
                                }
                            }

                            else -> {
                                Image(
                                    bitmap = state.data.qrBitmap.asImageBitmap(),
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态提示
            when (val state = uiState) {
                UiState.Loading -> Text(
                    "正在生成二维码...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is UiState.Error -> Unit

                is UiState.Success -> when (state.data.loginStatus) {
                    LoginStatus.Waiting -> Text(
                    "等待扫码...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                    LoginStatus.Scanned -> Text(
                        "已扫码，请在手机上确认",
                        color = MaterialTheme.colorScheme.primary
                    )

                    LoginStatus.Success -> Text(
                        "登录成功！",
                        color = MaterialTheme.colorScheme.primary
                    )

                    LoginStatus.Expired,
                    LoginStatus.Failed -> Unit
                }
            }
        }
    }
}
