package com.pcexplorer.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoConnect: Boolean = true,
    val bufferSizeKb: Int = 32,
    val parallelTransfers: Int = 2,
    val appVersion: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            themeMode = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name),
            autoConnect = prefs.getBoolean("auto_connect", true),
            bufferSizeKb = prefs.getInt("buffer_size_kb", 32),
            parallelTransfers = prefs.getInt("parallel_transfers", 2),
            appVersion = getAppVersion()
        )
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        _uiState.update { it.copy(autoConnect = enabled) }
    }

    fun setBufferSize(sizeKb: Int) {
        prefs.edit().putInt("buffer_size_kb", sizeKb).apply()
        _uiState.update { it.copy(bufferSizeKb = sizeKb) }
    }

    fun setParallelTransfers(count: Int) {
        prefs.edit().putInt("parallel_transfers", count).apply()
        _uiState.update { it.copy(parallelTransfers = count) }
    }
}
