package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import com.ronaldcolocho.taskly.domain.model.AppSettings
import com.ronaldcolocho.taskly.domain.repository.ISettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : ISettingsRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSettings = MutableStateFlow(AppSettings())

    override val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    init {
        scope.launch {
            mutableSettings.value = AppSettings(
                darkTheme = preferences.getBoolean(KEY_DARK_THEME, true),
                automaticDownloadsEnabled = preferences.getBoolean(KEY_AUTOMATIC_DOWNLOADS, false)
            )
        }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            preferences.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
            mutableSettings.update { it.copy(darkTheme = enabled) }
        }
    }

    override suspend fun setAutomaticDownloadsEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            preferences.edit().putBoolean(KEY_AUTOMATIC_DOWNLOADS, enabled).apply()
            mutableSettings.update { it.copy(automaticDownloadsEnabled = enabled) }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "taskly_settings"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_AUTOMATIC_DOWNLOADS = "automatic_downloads_enabled"
    }
}
