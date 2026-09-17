package xyz.qiaosheng.bilibili.ui.video.player

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import xyz.qiaosheng.bilibili.core.android.findActivity
import xyz.qiaosheng.bilibili.ui.theme.LocalDarkTheme

/** 全屏期间接管方向、系统栏和常亮标志，退出时恢复原窗口设置。 */
@Composable
internal fun ImmersiveFullscreenEffect(enable: Boolean) {
    val activity = LocalContext.current.findActivity()
    val lightSystemBars = rememberUpdatedState(!LocalDarkTheme.current)

    if (!enable || activity == null) return

    DisposableEffect(activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        val previousOrientation = activity.requestedOrientation
        val keepScreenOnWasSet = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

        val previousSystemBarsBehavior = controller.systemBarsBehavior

        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.hide(WindowInsetsCompat.Type.systemBars())

        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            // 全屏期间主题可能已改变，退出时使用当前主题恢复系统栏颜色。
            controller.isAppearanceLightStatusBars = lightSystemBars.value
            controller.isAppearanceLightNavigationBars = lightSystemBars.value
            controller.systemBarsBehavior = previousSystemBarsBehavior
            activity.requestedOrientation = previousOrientation

            if (!keepScreenOnWasSet) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
