package xyz.qiaosheng.bilibili.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode

class ThemePaletteTest {
    @Test fun everyFixedPaletteProvidesReadableTextInBothAppearances() {
        val fixedColors = ThemeColor.entries.filter { it != ThemeColor.DYNAMIC }
        for (dark in listOf(false, true)) {
            assertEquals(fixedColors.size, fixedColors.map { fixedColorScheme(it, dark).primary }.distinct().size)
            for (color in fixedColors) {
                val scheme = fixedColorScheme(color, dark)
                val pairs = listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceVariant to scheme.onSurfaceVariant,
                    scheme.surfaceContainerHighest to scheme.onSurface
                )
                for ((background, foreground) in pairs) {
                    assertTrue("$color, dark=$dark has unreadable foreground/background", 
                        contrast(background, foreground) >= 4.5)
                }
            }
        }
    }

    @Test fun explicitAppearanceDoesNotChangeWithTheSystem() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.SYSTEM.isDark(systemDark))
            assertEquals(false, ThemeMode.LIGHT.isDark(systemDark))
            assertEquals(true, ThemeMode.DARK.isDark(systemDark))
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
