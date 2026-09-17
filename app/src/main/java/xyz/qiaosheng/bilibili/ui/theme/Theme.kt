package xyz.qiaosheng.bilibili.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import xyz.qiaosheng.bilibili.model.settings.ThemeColor

internal val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun BilibiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: ThemeColor = ThemeColor.PINK,
    content: @Composable () -> Unit
) {
    val colorScheme = if (
        themeColor == ThemeColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        fixedColorScheme(themeColor, darkTheme)
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
