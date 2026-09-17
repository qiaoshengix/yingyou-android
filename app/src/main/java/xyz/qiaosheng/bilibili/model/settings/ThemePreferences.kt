package xyz.qiaosheng.bilibili.model.settings

/** 存储值与枚举名称分离，重命名枚举时仍可读取已有的主题偏好。 */
enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

enum class ThemeColor(val storedValue: String) {
    PINK("pink"),
    BLUE("blue"),
    PURPLE("purple"),
    GREEN("green"),
    ORANGE("orange"),
    DYNAMIC("dynamic")
}

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val color: ThemeColor = ThemeColor.PINK
)
