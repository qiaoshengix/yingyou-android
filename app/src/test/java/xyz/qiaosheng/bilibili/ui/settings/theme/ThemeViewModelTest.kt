package xyz.qiaosheng.bilibili.ui.settings.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.data.local.preferences.ThemeDataStore
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode
import xyz.qiaosheng.bilibili.model.settings.ThemePreferences

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val store = FakePreferencesDataStore()
    private val preferences = ThemeDataStore(store)
    private val logged = mutableListOf<Throwable>()
    private lateinit var viewModel: ThemeViewModel

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }

    @After fun teardown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun create() {
        viewModel = ThemeViewModel(preferences, ErrorReporter { _, error -> logged += error })
    }

    @Test fun readsSavedPreferencesAndTracksFurtherChanges() = runTest {
        val saved = ThemePreferences(ThemeMode.DARK, ThemeColor.BLUE)
        preferences.save(saved)
        create()
        runCurrent()
        assertEquals(saved, viewModel.uiState.value.preferences)
        assertTrue(viewModel.uiState.value.canEdit)

        val changed = ThemePreferences(ThemeMode.LIGHT, ThemeColor.GREEN)
        preferences.save(changed)
        runCurrent()
        assertEquals(changed, viewModel.uiState.value.preferences)
    }

    @Test fun readFailureCanBeRetriedWithoutOverwritingSavedSettings() = runTest {
        val saved = ThemePreferences(ThemeMode.DARK, ThemeColor.ORANGE)
        preferences.save(saved)
        store.readError = IOException("disk temporarily unavailable")
        create()
        runCurrent()
        assertNotNull(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canEdit)
        val writesBefore = store.writeCount
        viewModel.selectMode(ThemeMode.LIGHT)
        runCurrent()
        assertEquals(writesBefore, store.writeCount)

        store.readError = null
        viewModel.retryLoading()
        runCurrent()
        assertNull(viewModel.uiState.value.loadError)
        assertEquals(saved, viewModel.uiState.value.preferences)
        assertTrue(viewModel.uiState.value.canEdit)
    }

    @Test fun failedSaveKeepsCurrentThemeAndRetryAppliesTheRequestedTheme() = runTest {
        create()
        runCurrent()
        store.writeError = IOException("disk full")
        viewModel.selectMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.preferences.mode)
        assertNotNull(viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isSaving)

        store.writeError = null
        viewModel.retrySaving()
        runCurrent()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.preferences.mode)
        assertEquals(ThemeMode.DARK, preferences.preferences.first().mode)
        assertNull(viewModel.uiState.value.saveError)
        assertEquals(1, logged.size)
    }

    @Test fun switchingFromDynamicToBluePreservesDarkMode() = runTest {
        preferences.save(ThemePreferences(ThemeMode.DARK, ThemeColor.DYNAMIC))
        create()
        runCurrent()
        viewModel.selectColor(ThemeColor.BLUE)
        runCurrent()
        assertEquals(ThemePreferences(ThemeMode.DARK, ThemeColor.BLUE),
            viewModel.uiState.value.preferences)
        assertEquals(viewModel.uiState.value.preferences, preferences.preferences.first())
    }

    @Test fun writesAreGuardedUntilPersistenceFinishes() = runTest {
        create()
        runCurrent()
        store.writeDelay = 1_000
        viewModel.selectMode(ThemeMode.DARK)
        viewModel.selectColor(ThemeColor.BLUE)
        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals(ThemePreferences(), viewModel.uiState.value.preferences)
        advanceUntilIdle()
        assertEquals(1, store.writeCount)
        assertEquals(ThemePreferences(ThemeMode.DARK, ThemeColor.PINK),
            viewModel.uiState.value.preferences)

        viewModel.selectColor(ThemeColor.BLUE)
        advanceUntilIdle()
        assertEquals(ThemePreferences(ThemeMode.DARK, ThemeColor.BLUE),
            viewModel.uiState.value.preferences)
    }

    @Test fun cancelledSaveIsNotReportedAsAUserError() = runTest {
        create()
        runCurrent()
        store.writeError = CancellationException("cancelled")
        viewModel.selectMode(ThemeMode.DARK)
        runCurrent()
        assertNull(viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(ThemePreferences(), viewModel.uiState.value.preferences)
        assertTrue(logged.isEmpty())
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val stored = MutableStateFlow<Preferences>(emptyPreferences())
        var readError: Exception? = null
        var writeError: Exception? = null
        var writeDelay: Long = 0
        var writeCount = 0

        override val data: Flow<Preferences> = flow {
            readError?.let { throw it }
            emitAll(stored)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeError?.let { throw it }
            delay(writeDelay)
            return transform(stored.value).also {
                writeCount++
                stored.value = it
            }
        }
    }
}
