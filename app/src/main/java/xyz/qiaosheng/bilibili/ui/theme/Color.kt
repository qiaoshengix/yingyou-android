package xyz.qiaosheng.bilibili.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import xyz.qiaosheng.bilibili.model.settings.ThemeColor

private data class AccentPalette(
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color
)

private fun palette(color: ThemeColor): AccentPalette = when (color) {
    ThemeColor.PINK, ThemeColor.DYNAMIC -> AccentPalette(
        Color(0xFFB62961), Color(0xFFFFD9E3), Color(0xFF3E001B),
        Color(0xFFFFACC8), Color(0xFF650033), Color(0xFF8A1B49)
    )
    ThemeColor.BLUE -> AccentPalette(
        Color(0xFF005DB7), Color(0xFFD5E3FF), Color(0xFF001B3F),
        Color(0xFFA7C8FF), Color(0xFF003064), Color(0xFF00458C)
    )
    ThemeColor.PURPLE -> AccentPalette(
        Color(0xFF6D43B1), Color(0xFFEBDDFF), Color(0xFF250059),
        Color(0xFFD3BBFF), Color(0xFF3F137C), Color(0xFF562997)
    )
    ThemeColor.GREEN -> AccentPalette(
        Color(0xFF246B3E), Color(0xFFA8F4B6), Color(0xFF00210B),
        Color(0xFF8DD89C), Color(0xFF003919), Color(0xFF07522A)
    )
    ThemeColor.ORANGE -> AccentPalette(
        Color(0xFF8B5000), Color(0xFFFFDDBA), Color(0xFF2C1700),
        Color(0xFFFFB86A), Color(0xFF4A2800), Color(0xFF693C00)
    )
}

/** Every fixed accent has matching foreground/container roles in both appearances. */
internal fun fixedColorScheme(color: ThemeColor, dark: Boolean): ColorScheme {
    val accent = palette(color)
    return if (dark) {
        darkColorScheme(
            primary = accent.dark,
            onPrimary = accent.onDark,
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.lightContainer,
            secondary = accent.dark,
            onSecondary = accent.onDark,
            secondaryContainer = accent.darkContainer,
            onSecondaryContainer = accent.lightContainer,
            tertiary = accent.dark,
            onTertiary = accent.onDark,
            tertiaryContainer = accent.darkContainer,
            onTertiaryContainer = accent.lightContainer,
            background = Color(0xFF121316),
            onBackground = Color(0xFFE3E2E7),
            surface = Color(0xFF121316),
            onSurface = Color(0xFFE3E2E7),
            surfaceVariant = Color(0xFF45464D),
            onSurfaceVariant = Color(0xFFC6C6CE),
            surfaceTint = accent.dark,
            inversePrimary = accent.light,
            surfaceDim = Color(0xFF121316),
            surfaceBright = Color(0xFF38393D),
            surfaceContainerLowest = Color(0xFF0D0E11),
            surfaceContainerLow = Color(0xFF1B1C20),
            surfaceContainer = Color(0xFF1F2024),
            surfaceContainerHigh = Color(0xFF292A2E),
            surfaceContainerHighest = Color(0xFF343539)
        )
    } else {
        lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.onLightContainer,
            secondary = accent.light,
            onSecondary = Color.White,
            secondaryContainer = accent.lightContainer,
            onSecondaryContainer = accent.onLightContainer,
            tertiary = accent.light,
            onTertiary = Color.White,
            tertiaryContainer = accent.lightContainer,
            onTertiaryContainer = accent.onLightContainer,
            background = Color(0xFFFAFAFC),
            onBackground = Color(0xFF1B1C20),
            surface = Color(0xFFFAFAFC),
            onSurface = Color(0xFF1B1C20),
            surfaceVariant = Color(0xFFE2E2E9),
            onSurfaceVariant = Color(0xFF45464D),
            surfaceTint = accent.light,
            inversePrimary = accent.dark,
            surfaceDim = Color(0xFFDADAE0),
            surfaceBright = Color(0xFFFAFAFC),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF4F4F8),
            surfaceContainer = Color(0xFFEEEEF3),
            surfaceContainerHigh = Color(0xFFE8E8ED),
            surfaceContainerHighest = Color(0xFFE2E2E8)
        )
    }
}
