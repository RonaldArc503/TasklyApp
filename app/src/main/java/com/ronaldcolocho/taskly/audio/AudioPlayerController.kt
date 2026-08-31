package com.ronaldcolocho.taskly.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext context: Context
) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val dur = player.duration.takeIf { it > 0 } ?: 0
                        _state.update { it.copy(durationMs = dur) }
                    }
                    Player.STATE_ENDED -> onEnded()
                }
            }
        })
    }

    private fun onEnded() {
        val s = _state.value
        val track = s.currentTrack ?: return stop()
        when (s.mode) {
            AudioMode.REPEAT -> {
                player.seekTo(0)
                player.play()
            }
            AudioMode.LOOP -> {
                val idx = s.tracks.indexOfFirst { it.id == track.id }
                if (idx >= 0 && s.tracks.isNotEmpty()) playTrack(s.tracks[(idx + 1) % s.tracks.size])
                else stop()
            }
            AudioMode.QUEUE -> {
                val idx = s.tracks.indexOfFirst { it.id == track.id }
                if (idx >= 0 && idx < s.tracks.size - 1) playTrack(s.tracks[idx + 1])
                else stop()
            }
        }
    }

    fun updatePlaylist(tracks: List<AudioTrack>) {
        _state.update { it.copy(tracks = tracks) }
    }

    fun playWithQueue(track: AudioTrack) {
        _state.update { it.copy(mode = AudioMode.QUEUE) }
        playTrack(track)
    }

    fun playWithRepeat(track: AudioTrack) {
        _state.update { it.copy(mode = AudioMode.REPEAT) }
        playTrack(track)
    }

    fun playWithLoop(track: AudioTrack) {
        _state.update { it.copy(mode = AudioMode.LOOP) }
        playTrack(track)
    }

    fun toggleTrack(track: AudioTrack) {
        if (_state.value.currentTrack?.id == track.id) toggleCurrent() else playTrack(track)
    }

    fun toggleCurrent() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun updateMode(mode: AudioMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun next() {
        val s = _state.value
        if (s.tracks.isEmpty()) return
        val cur = s.currentTrack
        val idx = if (cur != null) s.tracks.indexOfFirst { it.id == cur.id } else -1
        playTrack(if (idx >= 0) s.tracks[(idx + 1) % s.tracks.size] else s.tracks[0])
    }

    fun prev() {
        val s = _state.value
        if (s.tracks.isEmpty()) return
        val cur = s.currentTrack
        val idx = if (cur != null) s.tracks.indexOfFirst { it.id == cur.id } else -1
        playTrack(if (idx > 0) s.tracks[idx - 1] else s.tracks.last())
    }

    fun seek(ms: Long) {
        player.seekTo(ms)
        _state.update { it.copy(currentTimeMs = ms) }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _state.value = AudioPlayerState()
        stopTicker()
    }

    private fun playTrack(track: AudioTrack) {
        _state.update {
            it.copy(currentTrack = track, currentTimeMs = 0, durationMs = 0, playing = true)
        }
        player.setMediaItem(MediaItem.fromUri(track.url))
        player.prepare()
        player.play()
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val pos = player.currentPosition.takeIf { it >= 0 } ?: 0
                _state.update { it.copy(currentTimeMs = pos) }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun release() {
        player.release()
        stopTicker()
        scope.cancel()
    }
}
