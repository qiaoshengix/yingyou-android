package xyz.qiaosheng.bilibili.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 持久化登录 Cookie；存储文件名和键保持稳定，以兼容已经保存的会话。 */
@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val Context.dataStore by preferencesDataStore("auth")

    private val SESSDATA_KEY = stringPreferencesKey("SESSDATA")
    private val BILI_JCT_KEY = stringPreferencesKey("bili_jct")
    private val DED_USER_ID_KEY = stringPreferencesKey("DedeUserID")

    suspend fun saveCookie(sessdata: String, biliJct: String, dedUserId: String) {
        context.dataStore.edit { prefs ->
            prefs[SESSDATA_KEY] = sessdata
            prefs[BILI_JCT_KEY] = biliJct
            prefs[DED_USER_ID_KEY] = dedUserId
        }
    }

    // 读取 Cookie
    suspend fun getCookies(): Triple<String?, String?, String?> {
        val prefs = context.dataStore.data.first()
        return Triple(
            prefs[SESSDATA_KEY],
            prefs[BILI_JCT_KEY],
            prefs[DED_USER_ID_KEY]
        )
    }

    suspend fun clearCookies() {
        context.dataStore.edit { prefs ->
            prefs.remove(SESSDATA_KEY)
            prefs.remove(BILI_JCT_KEY)
            prefs.remove(DED_USER_ID_KEY)
        }
    }
}
