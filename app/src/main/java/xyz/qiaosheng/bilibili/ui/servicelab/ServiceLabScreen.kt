package xyz.qiaosheng.bilibili.ui.servicelab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceLabScreen(
    onBack: () -> Unit,
    viewModel: ServiceLabViewModel = hiltViewModel()
) {
    val state by viewModel.serviceState.collectAsStateWithLifecycle()
    val isBound by viewModel.isBound.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service 实验室") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "计时：${state.elapsedSeconds} 秒",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = if (state.isRunning) "状态：运行中" else "状态：已暂停"
            )
            Text(
                text = if (isBound) "Binder：已连接" else "Binder：未连接"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::startService,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("启动 Service")
                }
                OutlinedButton(
                    onClick = viewModel::stopService,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止 Service")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::bindService,
                    enabled = !isBound,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("绑定")
                }
                OutlinedButton(
                    onClick = viewModel::unbindService,
                    enabled = isBound,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("解绑")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::startThroughBinder,
                    enabled = isBound,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Binder 继续")
                }
                OutlinedButton(
                    onClick = viewModel::pauseThroughBinder,
                    enabled = isBound,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Binder 暂停")
                }
                OutlinedButton(
                    onClick = viewModel::resetThroughBinder,
                    enabled = isBound,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清零")
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Service 生命周期日志")
                OutlinedButton(onClick = viewModel::clearLifecycleEvents) {
                    Text("清空日志")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.lifecycleEvents) { event ->
                    Text(
                        text = event,
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
