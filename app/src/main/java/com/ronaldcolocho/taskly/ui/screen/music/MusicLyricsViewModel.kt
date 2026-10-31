package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.data.music.LyricsRepository
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.LyricsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class MusicLyricsState(
    val trackId: String? = null, val loading: Boolean = false, val result: LyricsLookup? = null,
    val retrySeconds: Int = 0, val autoRetry: Boolean = false
)

@HiltViewModel
class MusicLyricsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    controller: AudioPlayerController,
    private val repository: LyricsRepository
) : ViewModel() {
    val showing = savedState.getStateFlow("music_show_lyrics", false)
    private val _state = MutableStateFlow(MusicLyricsState())
    val state = _state.asStateFlow()
    private val retries = MutableStateFlow(0)
    private val screenActive = MutableStateFlow(false)

    init {
        val selection = controller.state.map { player ->
            player.currentTrack to (player.currentTrack?.durationSeconds?.takeIf { it > 0 }
                ?: (player.durationMs / 1000).toInt())
        }.distinctUntilChanged().map { (track, duration) ->
            track?.let { it.id to LyricsQuery.from(it.name, it.artist, duration) }
        }
        viewModelScope.launch {
            var lastRetry = 0
            val visible = combine(showing, screenActive) { mode, active -> mode && active }
            combine(visible, selection, retries) { active, track, retry -> Triple(active, track, retry) }
                .collectLatest { (visible, selection, retry) ->
                    val refresh = retry != lastRetry
                    lastRetry = retry
                    if (!visible || selection == null) return@collectLatest
                    val (id, query) = selection
                    var attempt = 0
                    while (true) {
                        _state.value = MusicLyricsState(id, loading = true)
                        val result = repository.lookup(query, refresh && attempt == 0)
                        val deadline = (result as? LyricsLookup.Failed)?.retryAtMs
                        val automatic = deadline != null && attempt < 2
                        _state.value = MusicLyricsState(id, result = result)
                        if (deadline == null) break
                        while (true) {
                            val remaining = deadline - System.currentTimeMillis()
                            if (remaining <= 0) break
                            _state.value = MusicLyricsState(id, result = result,
                                retrySeconds = ((remaining + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                autoRetry = automatic)
                            delay(minOf(1000L, remaining))
                        }
                        _state.value = MusicLyricsState(id, result = result)
                        if (!automatic) break
                        attempt++
                    }
                }
        }
    }

    fun toggle() { savedState["music_show_lyrics"] = !showing.value }
    fun setScreenActive(active: Boolean) { screenActive.value = active }
    fun retry() {
        if (_state.value.loading || _state.value.retrySeconds > 0) return
        retries.update { it + 1 }
    }
}
