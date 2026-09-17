package xyz.qiaosheng.bilibili.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.qiaosheng.bilibili.model.library.LibraryKind
import xyz.qiaosheng.bilibili.model.library.LibraryVideo
import xyz.qiaosheng.bilibili.ui.library.components.LibraryVideoCard
import xyz.qiaosheng.bilibili.ui.library.components.LibraryEmptyContent
import xyz.qiaosheng.bilibili.ui.mine.MineUiState
import xyz.qiaosheng.bilibili.ui.mine.components.MineContent

/** 保护本地功能入口和跨页面参数：这些交互不应依赖远程个人资料成功返回。 */
class LibraryComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun guestCanOpenLocalHistoryAndOfflineWhileLoginRemainsAvailable() {
        var historyClicks = 0
        var offlineClicks = 0
        var loginClicks = 0
        compose.setContent {
            MaterialTheme {
                MineContent(
                    state = MineUiState.LoggedOut,
                    contentPadding = PaddingValues(),
                    onNavigateToHistory = { historyClicks++ },
                    onNavigateToFavorites = {},
                    onNavigateToLikes = {},
                    onNavigateToOffline = { offlineClicks++ },
                    onNavigateToLogin = { loginClicks++ },
                    onNavigateToServiceLab = {},
                    onRefreshProfile = {},
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("历史记录").performScrollTo().performClick()
        compose.onNodeWithText("离线缓存").performScrollTo().performClick()
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, historyClicks)
            assertEquals(1, offlineClicks)
            assertEquals(1, loginClicks)
        }
    }

    @Test fun profileFailureDoesNotRemoveMenusOrRetry() {
        var favoritesClicks = 0
        var likesClicks = 0
        var retries = 0
        compose.setContent {
            MaterialTheme {
                MineContent(
                    state = MineUiState.Error("网络不可用"),
                    contentPadding = PaddingValues(),
                    onNavigateToHistory = {},
                    onNavigateToFavorites = { favoritesClicks++ },
                    onNavigateToLikes = { likesClicks++ },
                    onNavigateToOffline = {},
                    onNavigateToLogin = {},
                    onNavigateToServiceLab = {},
                    onRefreshProfile = { retries++ },
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("我的收藏").performScrollTo().performClick()
        compose.onNodeWithText("我的点赞").performScrollTo().performClick()
        compose.onNodeWithText("离线缓存").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重试").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, favoritesClicks)
            assertEquals(1, likesClicks)
            assertEquals(1, retries)
        }
    }

    @Test fun loadingProfileKeepsLocalNavigationAvailable() {
        var openedHistory = false
        compose.setContent {
            MaterialTheme {
                MineContent(
                    state = MineUiState.Loading,
                    contentPadding = PaddingValues(),
                    onNavigateToHistory = { openedHistory = true },
                    onNavigateToFavorites = {},
                    onNavigateToLikes = {},
                    onNavigateToOffline = {},
                    onNavigateToLogin = {},
                    onNavigateToServiceLab = {},
                    onRefreshProfile = {},
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("历史记录").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, openedHistory) }
    }

    @Test fun continueWatchingPassesTheOriginalPartAndProgress() {
        val video = LibraryVideo(
            bvid = "BV1testPart", cid = 2002, title = "第二分P",
            durationSeconds = 600, progressSeconds = 125
        )
        var opened: LibraryVideo? = null
        compose.setContent {
            MaterialTheme {
                LibraryVideoCard(video, LibraryKind.HISTORY, false, { opened = it }, {})
            }
        }
        compose.onNodeWithText("继续观看").performClick()
        compose.runOnIdle { assertEquals(video, opened) }
    }

    @Test fun removalMenuKeepsFavoritesAndLikesActionsDistinct() {
        val video = LibraryVideo(bvid = "BV1remove", title = "已保存的视频")
        val kind = mutableStateOf(LibraryKind.FAVORITES)
        val removed = mutableListOf<Pair<LibraryKind, LibraryVideo>>()
        compose.setContent {
            MaterialTheme {
                LibraryVideoCard(video, kind.value, false, {}, { removed += kind.value to it })
            }
        }
        compose.onNodeWithContentDescription("更多操作：已保存的视频").performClick()
        compose.onNodeWithText("取消收藏").performClick()
        compose.runOnIdle { kind.value = LibraryKind.LIKES }
        compose.onNodeWithContentDescription("更多操作：已保存的视频").performClick()
        compose.onNodeWithText("取消点赞").performClick()
        compose.runOnIdle {
            assertEquals(listOf(LibraryKind.FAVORITES to video, LibraryKind.LIKES to video), removed)
        }
    }

    @Test fun completedRemoteHistoryOffersReplayInsteadOfMissingProgress() {
        val video = LibraryVideo(
            bvid = "BV1finished", cid = 42, title = "已完成的远端历史",
            durationSeconds = 300, progressSeconds = -1
        )
        var opened: LibraryVideo? = null
        compose.setContent {
            MaterialTheme {
                LibraryVideoCard(video, LibraryKind.HISTORY, false, { opened = it }, {})
            }
        }
        compose.onNodeWithText("已看完").assertIsDisplayed()
        compose.onNodeWithText("重新观看").performClick()
        compose.runOnIdle { assertEquals(video, opened) }
    }

    @Test fun expiredHistorySessionUsesHistoryLoginPrompt() {
        var loginClicks = 0
        compose.setContent {
            MaterialTheme {
                LibraryEmptyContent(
                    kind = LibraryKind.HISTORY,
                    loading = false,
                    loginRequired = true,
                    error = null,
                    onLogin = { loginClicks++ },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("登录后同步观看历史").assertIsDisplayed()
        compose.onNodeWithText("登录").performClick()
        compose.runOnIdle { assertEquals(1, loginClicks) }
    }
}
