package com.ronaldcolocho.taskly.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerColorsController @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("player_colors_v2", Context.MODE_PRIVATE)
    private val policy = PlayerColorPolicy()
    private val _state = MutableStateFlow(policy.restore(PlayerColorState(
        mode = runCatching { PlayerColorMode.valueOf(prefs.getString("mode", null).orEmpty()) }
            .getOrDefault(PlayerColorMode.AUTOMATIC),
        trackId = prefs.getString("track", null),
        paletteId = prefs.getString("palette", null).orEmpty(),
        recentPaletteIds = prefs.getString("recent", null).orEmpty().split('|').filter { it.isNotBlank() }
    )))
    val state = _state.asStateFlow()

    fun onTrackChanged(trackId: String?) = update(policy.onTrackChanged(_state.value, trackId))
    fun setMode(mode: PlayerColorMode) = update(policy.setMode(_state.value, mode))
    fun selectPalette(paletteId: String): PlayerColorState {
        update(policy.selectPalette(_state.value, paletteId))
        return _state.value
    }
    fun changeColor(): PlayerColorState {
        update(policy.changeColor(_state.value))
        return _state.value
    }

    private fun update(next: PlayerColorState) {
        if (next == _state.value) return
        _state.value = next
        prefs.edit().putString("mode", next.mode.name).putString("track", next.trackId)
            .putString("palette", next.paletteId).putString("recent", next.recentPaletteIds.joinToString("|"))
            .apply()
    }
}
