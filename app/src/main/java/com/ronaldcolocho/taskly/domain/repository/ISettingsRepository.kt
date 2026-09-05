package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface ISettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setAutomaticDownloadsEnabled(enabled: Boolean)
}
