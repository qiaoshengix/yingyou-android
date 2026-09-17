package xyz.qiaosheng.bilibili.ui.video.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.qiaosheng.bilibili.ui.video.DanmakuUiState

/** 验证控件的用户操作和无障碍状态，播放/存储行为由独立状态测试覆盖。 */
class PlayerTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggleReflectsStateAndWaitsForPersistedPreference() {
        val state = mutableStateOf(DanmakuUiState())
        var changes = 0
        compose.setContent {
            MaterialTheme {
                PlayerTopBar(
                    false, false, state.value, {}, {},
                    onDanmakuEnabledChange = { state.value = state.value.copy(enabled = it); changes++ },
                    onRetryDanmaku = {},
                )
            }
        }
        compose.onNodeWithContentDescription("弹幕开关").assertIsNotEnabled().assertIsOn()
        compose.runOnIdle { state.value = state.value.copy(preferencesReady = true, loading = false) }
        compose.onNodeWithContentDescription("弹幕开关").performClick().assertIsOff()
        compose.onNodeWithContentDescription("弹幕开关").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(2, changes) }
    }

    @Test fun fullscreenReturnAndAudioActionRemainIndependent() {
        val fullScreen = mutableStateOf(true)
        val audioOnly = mutableStateOf(false)
        var exits = 0
        compose.setContent {
            MaterialTheme {
                PlayerTopBar(
                    fullScreen.value, audioOnly.value,
                    DanmakuUiState(preferencesReady = true, loading = false),
                    onExitFullScreen = { exits++; fullScreen.value = false },
                    onToggleBackgroundAudio = { audioOnly.value = !audioOnly.value },
                    onDanmakuEnabledChange = {}, onRetryDanmaku = {},
                )
            }
        }
        compose.onNodeWithText("后台听").performClick()
        compose.onNodeWithText("恢复画面").assertExists().performClick()
        compose.onNodeWithText("后台听").assertExists()
        compose.onNodeWithContentDescription("退出全屏并返回").performClick().assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, exits); assertEquals(false, audioOnly.value) }
    }

    @Test fun retryIsAvailableOnlyForRecoverableError() {
        val state = mutableStateOf(DanmakuUiState(preferencesReady = true, loading = false, errorMessage = "网络暂时不可用"))
        var retries = 0
        compose.setContent {
            MaterialTheme {
                PlayerTopBar(false, false, state.value, {}, {}, {}, { retries++ })
            }
        }
        compose.onNodeWithText("网络暂时不可用").assertExists()
        compose.onNodeWithText("重试弹幕").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = state.value.copy(errorMessage = null, unavailableReason = "离线播放暂不支持弹幕")
        }
        compose.onNodeWithText("离线播放暂不支持弹幕").assertExists()
        compose.onNodeWithText("重试弹幕").assertDoesNotExist()
    }
}
