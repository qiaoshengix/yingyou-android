package xyz.qiaosheng.bilibili.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode
import xyz.qiaosheng.bilibili.model.settings.ThemePreferences

// 每个文件只创建一个 DataStore；主题与登录 Cookie 分开保存，退出登录时仍保留外观偏好。
private val Context.themeDataStore by preferencesDataStore(name = "theme")

/** 持久化外观偏好，读取未知存储值时回退到默认主题。 */
@Singleton
class ThemeDataStore internal constructor(private val dataStore: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.themeDataStore)

    val preferences: Flow<ThemePreferences> = dataStore.data
        .map(::readPreferences)
        .distinctUntilChanged()

    suspend fun save(preferences: ThemePreferences) {
        dataStore.edit { stored ->
            stored[MODE] = preferences.mode.storedValue
            stored[COLOR] = preferences.color.storedValue
        }
    }

    private fun readPreferences(stored: Preferences): ThemePreferences = ThemePreferences(
        mode = ThemeMode.entries.firstOrNull { it.storedValue == stored[MODE] }
            ?: ThemeMode.SYSTEM,
        color = ThemeColor.entries.firstOrNull { it.storedValue == stored[COLOR] }
            ?: ThemeColor.PINK
    )

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val COLOR = stringPreferencesKey("color")
    }
}
