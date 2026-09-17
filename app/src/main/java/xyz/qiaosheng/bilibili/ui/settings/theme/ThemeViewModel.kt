package xyz.qiaosheng.bilibili.ui.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.qiaosheng.bilibili.core.error.ErrorReporter
import xyz.qiaosheng.bilibili.core.error.suspendRunCatching
import xyz.qiaosheng.bilibili.data.local.preferences.ThemeDataStore
import xyz.qiaosheng.bilibili.model.settings.ThemeColor
import xyz.qiaosheng.bilibili.model.settings.ThemeMode
import xyz.qiaosheng.bilibili.model.settings.ThemePreferences

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
    private val errorReporter: ErrorReporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState = _uiState.asStateFlow()
    private var readJob: Job? = null
    private var pendingSave: ThemePreferences? = null

    init { retryLoading() }

    fun retryLoading() {
        if (readJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        readJob = viewModelScope.launch {
            themeDataStore.preferences
                .catch { error ->
                    if (error !is Exception) throw error
                    val message = errorReporter.message("读取主题设置失败", error)
                    _uiState.update { it.copy(isLoading = false, loadError = message) }
                }
                .collect { preferences ->
                    _uiState.update {
                        it.copy(preferences = preferences, isLoading = false, loadError = null)
                    }
                }
        }
    }

    fun selectMode(mode: ThemeMode) {
        save(_uiState.value.preferences.copy(mode = mode))
    }

    fun selectColor(color: ThemeColor) {
        save(_uiState.value.preferences.copy(color = color))
    }

    fun retrySaving() { pendingSave?.let(::save) }

    private fun save(preferences: ThemePreferences) {
        if (!_uiState.value.canEdit) return
        if (preferences == _uiState.value.preferences) {
            pendingSave = null
            _uiState.update { it.copy(saveError = null) }
            return
        }
        pendingSave = preferences
        // Mark busy before launching to prevent two taps in the same frame from racing.
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                suspendRunCatching { themeDataStore.save(preferences) }
                    .onSuccess {
                        pendingSave = null
                        _uiState.update { it.copy(preferences = preferences) }
                    }
                    .onFailure { error ->
                        val message = errorReporter.message("保存主题设置失败", error)
                        _uiState.update { it.copy(saveError = message) }
                    }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
