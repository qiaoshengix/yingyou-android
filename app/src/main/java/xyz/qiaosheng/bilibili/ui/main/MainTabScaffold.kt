package xyz.qiaosheng.bilibili.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import xyz.qiaosheng.bilibili.ui.navigation.MainTab
import xyz.qiaosheng.bilibili.ui.navigation.Routes

/** 底栏负责主标签选择，并只消费自己占用的 inset，避免页面重复计算。 */
@Composable
internal fun MainTabScaffold(
    selectedTab: MainTab,
    onSelectedTab: (MainTab) -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.HOME,
                    onClick = { onSelectedTab(MainTab.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.DYNAMIC,
                    onClick = { onSelectedTab(MainTab.DYNAMIC) },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "动态") },
                    label = { Text("动态") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.MINE,
                    onClick = { onSelectedTab(MainTab.MINE) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            content()
        }
    }
}
