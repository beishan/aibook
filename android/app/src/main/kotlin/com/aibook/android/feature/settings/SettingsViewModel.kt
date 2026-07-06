package com.aibook.android.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.prefs.ReaderSettingsStore
import com.aibook.android.core.data.prefs.ServerConfigStore
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val wifiOnlySync: Boolean = true,
    val personalizedRecommendations: Boolean = true,
    val usageStatistics: Boolean = true,
    val fontScale: Float = 1.0f,
    val lineHeight: Float = 1.45f,
    val readerTheme: ReaderTheme = ReaderTheme.PAPER,
    val loginFormUsername: String = "",
    val loginFormPassword: String = "",
    val isLoggingIn: Boolean = false,
    val loginMessage: String? = null,
    val serverUrlInput: String = ""
)

class SettingsViewModel(
    private val serverRepository: ServerRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val serverConfigStore: ServerConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.serverUrl.collect { url ->
                _state.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            serverRepository.isLoggedIn.collect { loggedIn ->
                _state.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            serverRepository.username.collect { username ->
                _state.update { it.copy(username = username) }
            }
        }
        viewModelScope.launch {
            serverConfigStore.wifiOnlySync.collect { wifiOnly ->
                _state.update { it.copy(wifiOnlySync = wifiOnly) }
            }
        }
        viewModelScope.launch {
            serverConfigStore.personalizedRecommendations.collect { enabled ->
                _state.update { it.copy(personalizedRecommendations = enabled) }
            }
        }
        viewModelScope.launch {
            serverConfigStore.usageStatistics.collect { enabled ->
                _state.update { it.copy(usageStatistics = enabled) }
            }
        }
        viewModelScope.launch {
            readerSettingsStore.fontScale.collect { scale ->
                _state.update { it.copy(fontScale = scale) }
            }
        }
        viewModelScope.launch {
            readerSettingsStore.lineHeight.collect { height ->
                _state.update { it.copy(lineHeight = height) }
            }
        }
        viewModelScope.launch {
            readerSettingsStore.theme.collect { theme ->
                _state.update { it.copy(readerTheme = theme) }
            }
        }
    }

    fun updateServerUrlInput(value: String) {
        _state.update { it.copy(serverUrlInput = value) }
    }

    fun saveServerUrl() {
        val url = _state.value.serverUrlInput.trim()
        if (url.isNotBlank()) {
            viewModelScope.launch { serverRepository.setServerUrl(url) }
        }
    }

    fun updateLoginUsername(value: String) {
        _state.update { it.copy(loginFormUsername = value) }
    }

    fun updateLoginPassword(value: String) {
        _state.update { it.copy(loginFormPassword = value) }
    }

    fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, loginMessage = null) }
            val result = serverRepository.login(
                _state.value.loginFormUsername,
                _state.value.loginFormPassword
            )
            _state.update { it.copy(isLoggingIn = false) }
            result.onSuccess { response ->
                _state.update {
                    it.copy(
                        loginMessage = "登录成功：${response.username}",
                        loginFormPassword = ""
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loginMessage = "登录失败：${e.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { serverRepository.logout() }
    }

    fun setWifiOnlySync(enabled: Boolean) {
        viewModelScope.launch { serverConfigStore.setWifiOnlySync(enabled) }
    }

    fun setPersonalizedRecommendations(enabled: Boolean) {
        viewModelScope.launch { serverConfigStore.setPersonalizedRecommendations(enabled) }
    }

    fun setUsageStatistics(enabled: Boolean) {
        viewModelScope.launch { serverConfigStore.setUsageStatistics(enabled) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { readerSettingsStore.setFontScale(value) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { readerSettingsStore.setLineHeight(value) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch { readerSettingsStore.setTheme(theme) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                SettingsViewModel(locator.serverRepository, locator.readerSettingsStore, locator.serverConfigStore)
            }
        }
    }
}
