package xyz.qiaosheng.bilibili.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import xyz.qiaosheng.bilibili.core.android.findActivity

/** 窗口图标随应用主题更新；播放器沉浸态仍使用适合黑色视频底色的白色图标。 */
@Composable
internal fun SystemBarsEffect(darkTheme: Boolean) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        val window = activity.window
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val fullscreen = insets != null &&
            !insets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
            !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
        val style = SystemBarStyle.auto(
            lightScrim = AndroidColor.TRANSPARENT,
            darkScrim = AndroidColor.TRANSPARENT,
            detectDarkMode = { darkTheme }
        )
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (fullscreen) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose { }
    }
}
