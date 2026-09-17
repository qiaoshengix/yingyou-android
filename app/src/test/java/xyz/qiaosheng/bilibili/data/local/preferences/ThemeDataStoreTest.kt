package xyz.qiaosheng.bilibili.data.local.preferences

import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode
import xyz.qiaosheng.bilibili.model.settings.ThemePreferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeDataStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun firstLaunchDefaultsToSystemAppearanceAndPink() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.root, "defaults.preferences_pb")
        }

        assertEquals(ThemePreferences(ThemeMode.SYSTEM, ThemeColor.PINK),
            ThemeDataStore(store).preferences.first())
    }

    @Test fun unknownStoredValuesFallBackIndependently() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.root, "unknown.preferences_pb")
        }
        store.edit {
            it[stringPreferencesKey("mode")] = "future-mode"
            it[stringPreferencesKey("color")] = "blue"
        }
        val preferences = ThemeDataStore(store)
        assertEquals(ThemePreferences(ThemeMode.SYSTEM, ThemeColor.BLUE), preferences.preferences.first())

        store.edit {
            it[stringPreferencesKey("mode")] = "dark"
            it[stringPreferencesKey("color")] = "future-color"
        }
        assertEquals(ThemePreferences(ThemeMode.DARK, ThemeColor.PINK), preferences.preferences.first())
    }

    @Test fun savedModeAndColorSurviveClosingAndReopeningTheFile() = runTest {
        val file = File(temporaryFolder.root, "restart.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + StandardTestDispatcher(testScheduler)),
            produceFile = { file }
        )
        val saved = ThemePreferences(ThemeMode.DARK, ThemeColor.BLUE)
        try {
            ThemeDataStore(firstStore).save(saved)
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        assertEquals(saved, ThemeDataStore(secondStore).preferences.first())
    }
}
