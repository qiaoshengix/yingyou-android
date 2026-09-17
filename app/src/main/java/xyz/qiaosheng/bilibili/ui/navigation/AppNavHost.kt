package xyz.qiaosheng.bilibili.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.ui.library.LibraryScreen
import xyz.qiaosheng.bilibili.ui.offline.OfflineScreen
import xyz.qiaosheng.bilibili.ui.dynamic.DynamicScreen
import xyz.qiaosheng.bilibili.ui.home.HomeScreen
import xyz.qiaosheng.bilibili.ui.login.LoginScreen
import xyz.qiaosheng.bilibili.ui.main.MainTabScaffold
import xyz.qiaosheng.bilibili.ui.mine.MineScreen
import xyz.qiaosheng.bilibili.ui.search.SearchScreen
import xyz.qiaosheng.bilibili.ui.servicelab.ServiceLabScreen
import xyz.qiaosheng.bilibili.ui.video.VideoDetailScreen

/** 应用唯一导航图；各页面只接收导航回调，不持有 NavController。 */
@Composable
fun AppNavHost(
    onOpenThemeSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(Routes.HOME) }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = modifier,

        // 返回时，下面的页面不做渐隐动画
        popEnterTransition = {
            EnterTransition.None
        },

        // 当前页面跟随返回手势缩小
        popExitTransition = {
            scaleOut(
                targetScale = 0.85f,
                transformOrigin = TransformOrigin(
                    pivotFractionX = 0.5f,
                    pivotFractionY = 0.5f
                ),
                animationSpec = tween(durationMillis = 300)
            )
        }
    ) {
        composable(Routes.MAIN) {
            MainTabScaffold(
                selectedTab = selectedTab,
                onSelectedTab = { selectedTab = it }
            ) {
                when (selectedTab) {
                    Routes.HOME -> {
                        HomeScreen(
                            viewModel = hiltViewModel(),
                            onNavigateToVideo = { bvid ->
                                navController.navigate(Routes.videoDetail(bvid))
                            },
                            onNavigateToSearch = {
                                navController.navigate(Routes.SEARCH)
                            }
                        )
                    }

                    Routes.DYNAMIC -> {
                        DynamicScreen(
                            onNavigateToLogin = {
                                navController.navigate(Routes.LOGIN)
                            },
                            onNavigateToVideo = { bvid ->
                                navController.navigate(Routes.videoDetail(bvid))
                            }
                        )
                    }

                    Routes.MINE -> {
                        MineScreen(
                            onOpenThemeSettings = onOpenThemeSettings,
                            onNavigateToLogin = {
                                navController.navigate(Routes.LOGIN)
                            },
                            onNavigateToHistory = {
                                navController.navigate(Routes.HISTORY)
                            },
                            onNavigateToFavorites = { navController.navigate(Routes.FAVORITES) },
                            onNavigateToLikes = { navController.navigate(Routes.LIKES) },
                            onNavigateToOffline = { navController.navigate(Routes.OFFLINE) },
                            onNavigateToServiceLab = {
                                navController.navigate(Routes.SERVICE_LAB)
                            }
                        )
                    }

                    else -> Unit
                }
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        listOf(
            Routes.HISTORY to LibraryKind.HISTORY,
            Routes.FAVORITES to LibraryKind.FAVORITES,
            Routes.LIKES to LibraryKind.LIKES
        ).forEach { (route, kind) ->
            composable(route) {
                LibraryScreen(
                    kind = kind,
                    onBack = { navController.popBackStack() },
                    onNavigateToVideo = { video ->
                        navController.navigate(Routes.videoDetail(video.bvid, video.cid))
                    },
                    onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
                )
            }
        }
        composable(Routes.OFFLINE) {
            OfflineScreen(
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { video ->
                    navController.navigate(Routes.videoDetail(video.bvid, video.cid, offline = true))
                }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToVideo = { bvid ->
                    navController.navigate(Routes.videoDetail(bvid))
                }
            )
        }
        composable(
            route = Routes.VIDEO_DETAIL,
            arguments = listOf(
                navArgument("cid") { type = NavType.LongType; defaultValue = 0L },
                navArgument("offline") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            VideoDetailScreen(
                backStackEntry = backStackEntry,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }
        composable(Routes.SERVICE_LAB) {
            ServiceLabScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
