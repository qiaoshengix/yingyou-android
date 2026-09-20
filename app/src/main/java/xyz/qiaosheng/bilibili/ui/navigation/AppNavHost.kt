package xyz.qiaosheng.bilibili.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.ui.dynamic.DynamicScreen
import xyz.qiaosheng.bilibili.ui.home.HomeScreen
import xyz.qiaosheng.bilibili.ui.library.LibraryScreen
import xyz.qiaosheng.bilibili.ui.login.LoginScreen
import xyz.qiaosheng.bilibili.ui.main.MainTabScaffold
import xyz.qiaosheng.bilibili.ui.mine.MineScreen
import xyz.qiaosheng.bilibili.ui.offline.OfflineScreen
import xyz.qiaosheng.bilibili.ui.search.SearchScreen
import xyz.qiaosheng.bilibili.ui.servicelab.ServiceLabScreen
import xyz.qiaosheng.bilibili.ui.video.VideoDetailScreen
import xyz.qiaosheng.bilibili.ui.video.VideoViewModel

/** 应用唯一导航图；各页面只接收导航回调，不持有 NavController。 */
@Composable
fun AppNavHost(
    onOpenThemeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Routes.Main)
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // 普通返回
        popTransitionSpec = {
            EnterTransition.None togetherWith
                    scaleOut(
                        targetScale = 0.85f,
                        transformOrigin = TransformOrigin(
                            pivotFractionX = 0.5f,
                            pivotFractionY = 0.5f
                        ),
                        animationSpec = tween(
                            durationMillis = 300
                        )
                    )
        },

        // 手势预测返回
        predictivePopTransitionSpec = {
            EnterTransition.None togetherWith
                    scaleOut(
                        targetScale = 0.85f,
                        transformOrigin = TransformOrigin(
                            pivotFractionX = 0.5f,
                            pivotFractionY = 0.5f
                        ),
                        animationSpec = tween(
                            durationMillis = 300
                        )
                    )
        },
        entryProvider = entryProvider {
            entry<Routes.Main> {
                MainTabScaffold(
                    selectedTab = selectedTab,
                    onSelectedTab = { selectedTab = it }
                ) {
                    when (selectedTab) {
                        MainTab.HOME -> {
                            HomeScreen(
                                viewModel = hiltViewModel(),
                                onNavigateToVideo = { bvid ->
                                    backStack.add(Routes.VideoDetail(bvid))
                                },
                                onNavigateToSearch = {
                                    backStack.add(Routes.Search)
                                }
                            )
                        }

                        MainTab.DYNAMIC -> {
                            DynamicScreen(
                                onNavigateToLogin = {
                                    backStack.add(Routes.Login)
                                },
                                onNavigateToVideo = { bvid ->
                                    backStack.add(Routes.VideoDetail(bvid))
                                }
                            )
                        }

                        MainTab.MINE -> {
                            MineScreen(
                                onOpenThemeSettings = onOpenThemeSettings,
                                onNavigateToLogin = {
                                    backStack.add(Routes.Login)
                                },
                                onNavigateToHistory = {
                                    backStack.add(Routes.History)
                                },
                                onNavigateToFavorites = { backStack.add(Routes.Favorites) },
                                onNavigateToLikes = { backStack.add(Routes.Likes) },
                                onNavigateToOffline = { backStack.add(Routes.Offline) },
                                onNavigateToServiceLab = {
                                    backStack.add(Routes.ServiceLab)
                                }
                            )
                        }
                    }
                }
            }

            entry<Routes.Login> {
                LoginScreen(
                    onLoginSuccess = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            listOf(
                Routes.History to LibraryKind.HISTORY,
                Routes.Favorites to LibraryKind.FAVORITES,
                Routes.Likes to LibraryKind.LIKES
            ).forEach { (route, kind) ->
                entry(route) {
                    LibraryScreen(
                        kind = kind,
                        onBack = { backStack.removeLastOrNull() },
                        onNavigateToVideo = { video ->
                            backStack.add(Routes.VideoDetail(video.bvid, video.cid))
                        },
                        onNavigateToLogin = { backStack.add(Routes.Login) }
                    )
                }
            }
            entry<Routes.Offline> {
                OfflineScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToVideo = { video ->
                        backStack.add(
                            Routes.VideoDetail(
                                video.bvid,
                                video.cid,
                                offline = true
                            )
                        )
                    }
                )
            }
            entry<Routes.Search> {
                SearchScreen(
                    onBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToVideo = { bvid ->
                        backStack.add(Routes.VideoDetail(bvid))
                    }
                )
            }
            entry<Routes.VideoDetail> { routesVideoDetail ->
                val viewModel = hiltViewModel<VideoViewModel, VideoViewModel.Factory>(
                    creationCallback = { factory ->
                        factory.create(
                            bvid = routesVideoDetail.bvid,
                            cid = routesVideoDetail.cid,
                            offline = routesVideoDetail.offline
                        )
                    }
                )
                VideoDetailScreen(
                    onBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateToLogin = {
                        backStack.add(Routes.Login)
                    },
                    viewModel = viewModel
                )
            }
            entry<Routes.ServiceLab> {
                ServiceLabScreen(
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
