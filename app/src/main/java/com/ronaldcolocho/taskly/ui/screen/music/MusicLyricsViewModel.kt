package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.audio.AudioPlayerController
import com.ronaldcolocho.taskly.data.music.LyricsRepository
import com.ronaldcolocho.taskly.data.music.LyricsTranslator
import com.ronaldcolocho.taskly.domain.model.LyricsLookup
import com.ronaldcolocho.taskly.domain.model.LyricsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class MusicLyricsState(
    val trackId: String? = null, val loading: Boolean = false, val result: LyricsLookup? = null,
    val retrySeconds: Int = 0, val autoRetry: Boolean = false,
    val translation: MusicLyricsTranslation? = null,
    val translationSource: String? = null
)

sealed interface MusicLyricsTranslation {
    data object Available : MusicLyricsTranslation
    data object Loading : MusicLyricsTranslation
    data object Failed : MusicLyricsTranslation
    data class Shown(val lines: List<String>) : MusicLyricsTranslation
}

@HiltViewModel
class MusicLyricsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val controller: AudioPlayerController,
    private val repository: LyricsRepository,
    private val translator: LyricsTranslator
) : ViewModel() {
    val showing = savedState.getStateFlow("music_show_lyrics", false)
    private val _state = MutableStateFlow(MusicLyricsState())
    val state = _state.asStateFlow()
    private val retries = MutableStateFlow(0)
    private val screenActive = MutableStateFlow(false)
    private var translationJob: Job? = null

    init {
        val selection = controller.state.map { player ->
            player.currentTrack to (player.currentTrack?.durationSeconds?.takeIf { it > 0 }
                ?: (player.durationMs / 1000).toInt())
        }.distinctUntilChanged().map { (track, duration) ->
            track?.let { it.id to LyricsQuery.from(it.name, it.artist, duration) }
        }
        viewModelScope.launch {
            var lastRetry = 0
            combine(screenActive, selection, retries) { active, track, retry -> Triple(active, track, retry) }
                .collectLatest { (visible, selection, retry) ->
                    translationJob?.cancel()
                    val refresh = retry != lastRetry
                    lastRetry = retry
                    if (!visible || selection == null) {
                        _state.update { it.copy(translation = null, translationSource = null) }
                        return@collectLatest
                    }
                    val (id, query) = selection
                    var attempt = 0
                    while (true) {
                        _state.value = MusicLyricsState(id, loading = true)
                        val result = repository.lookup(query, refresh && attempt == 0)
                        val deadline = (result as? LyricsLookup.Failed)?.retryAtMs
                        val automatic = deadline != null && (result as LyricsLookup.Failed).autoRetry && attempt < 2
                        _state.value = MusicLyricsState(id, result = result)
                        if (result is LyricsLookup.Found && !result.lyrics.instrumental) {
                            translationSource(result.lyrics.displayText)?.let { source ->
                                _state.update { it.copy(translation = MusicLyricsTranslation.Available, translationSource = source) }
                            }
                        }
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

    fun setShowing(showLyrics: Boolean) {
        savedState["music_show_lyrics"] = showLyrics
        if (!showLyrics) {
            translationJob?.cancel()
            _state.update { it.copy(translation = it.translationSource?.let { MusicLyricsTranslation.Available }) }
        }
    }
    fun setScreenActive(active: Boolean) { screenActive.value = active }

    fun toggleTranslation() {
        val current = _state.value
        if (!showing.value || !screenActive.value || current.trackId != controller.state.value.currentTrack?.id) return
        val lyrics = (current.result as? LyricsLookup.Found)?.lyrics ?: return
        val source = current.translationSource ?: return
        when (current.translation) {
            null -> return
            MusicLyricsTranslation.Loading, is MusicLyricsTranslation.Shown -> {
                translationJob?.cancel()
                _state.value = current.copy(translation = MusicLyricsTranslation.Available)
            }
            MusicLyricsTranslation.Available, MusicLyricsTranslation.Failed -> {
                val loading = current.copy(translation = MusicLyricsTranslation.Loading)
                _state.value = loading
                translationJob = viewModelScope.launch {
                    val lines = try {
                        withTimeoutOrNull(120_000) { translator.translate(source, lyrics.translationLines()) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (_state.value === loading && controller.state.value.currentTrack?.id == loading.trackId) {
                        _state.value = loading.copy(translation = lines?.let { MusicLyricsTranslation.Shown(it) }
                            ?: MusicLyricsTranslation.Failed)
                    }
                }
            }
        }
    }

    private suspend fun translationSource(text: String): String? = try {
        withTimeoutOrNull(5_000) { translator.sourceLanguage(text) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    fun retry() {
        if (_state.value.loading || _state.value.retrySeconds > 0) return
        retries.update { it + 1 }
    }
}
