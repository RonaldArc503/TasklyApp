package com.ronaldcolocho.taskly.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.repository.ISettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository
) : ViewModel() {
    val settings: StateFlow<com.ronaldcolocho.taskly.domain.model.AppSettings> = settingsRepository.settings

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    fun setAutomaticDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutomaticDownloadsEnabled(enabled) }
    }
}
