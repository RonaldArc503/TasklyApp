package com.ronaldcolocho.taskly.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    /** Resuelve el archivo local de una pista (si ya está descargado). */
    private var trackResolver: ((AudioTrack) -> File?)? = null

    /** Asegura (descarga si hace falta) y devuelve el archivo local de una pista. */
    private var ensureTrack: (suspend (AudioTrack) -> File?)? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    // ExoPlayer se construye de forma perezosa (solo al primer play) para no
    // penalizar el arranque/login.
    private var player: ExoPlayer? = null

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(playing = false) }
                android.util.Log.e("AudioPlayer", "Error de reproducción", error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val dur = p.duration.takeIf { it > 0 } ?: 0
                        _state.update { it.copy(durationMs = dur) }
                    }
                    Player.STATE_ENDED -> onEnded()
                }
            }
        })
        player = p
        return p
    }

    fun setTrackResolver(resolver: (AudioTrack) -> File?) {
        trackResolver = resolver
    }

    fun setEnsureTrack(resolver: suspend (AudioTrack) -> File?) {
        ensureTrack = resolver
    }

    private fun onEnded() {
        val s = _state.value
        val track = s.currentTrack ?: return stop()
        when (s.mode) {
            AudioMode.INDIVIDUAL -> stop()

            AudioMode.REPEAT -> {
                val p = ensurePlayer()
                p.seekTo(0)
                p.play()
            }

            AudioMode.LOOP -> {
                val idx = s.tracks.indexOfFirst { it.id == track.id }
                if (idx >= 0 && s.tracks.isNotEmpty()) {
                    advanceTo(s.tracks[(idx + 1) % s.tracks.size])
                } else {
                    stop()
                }
            }

            AudioMode.QUEUE -> {
                val idx = s.tracks.indexOfFirst { it.id == track.id }
                if (idx >= 0 && idx < s.tracks.size - 1) {
                    advanceTo(s.tracks[idx + 1])
                } else {
                    stop()
                }
            }
        }
    }

    /** Avanza a la siguiente pista descargándola si es necesario (single-flight). */
    private fun advanceTo(track: AudioTrack) {
        val resolver = ensureTrack ?: run {
            playTrack(track)
            return
        }
        scope.launch {
            val file = resolver(track)
            if (file != null) playLocalFile(track, file) else stop()
        }
    }

    fun updatePlaylist(tracks: List<AudioTrack>) {
        _state.update { it.copy(tracks = tracks) }
    }

    fun playWithIndividual(track: AudioTrack) {
        _state.update { it.copy(mode = AudioMode.INDIVIDUAL) }
        playTrack(track)
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
        val p = ensurePlayer()
        if (p.isPlaying) p.pause() else p.play()
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
        ensurePlayer().seekTo(ms)
        _state.update { it.copy(currentTimeMs = ms) }
    }

    fun stop() {
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        _state.update {
            it.copy(currentTrack = null, currentTimeMs = 0, durationMs = 0, playing = false)
        }
        stopTicker()
    }

    private fun playTrack(track: AudioTrack) {
        val localFile = trackResolver?.invoke(track)
        if (localFile != null && localFile.exists()) {
            playLocalFile(track, localFile)
        } else {
            playStream(track)
        }
    }

    private fun playLocalFile(track: AudioTrack, file: File) {
        _state.update {
            it.copy(currentTrack = track, currentTimeMs = 0, durationMs = 0, playing = true)
        }
        val p = ensurePlayer()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.prepare()
        p.play()
        startTicker()
    }

    private fun playStream(track: AudioTrack) {
        _state.update {
            it.copy(currentTrack = track, currentTimeMs = 0, durationMs = 0, playing = true)
        }
        val p = ensurePlayer()
        p.setMediaItem(MediaItem.fromUri(track.url))
        p.prepare()
        p.play()
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            val p = ensurePlayer()
            while (isActive) {
                val pos = p.currentPosition.takeIf { it >= 0 } ?: 0
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
        player?.release()
        stopTicker()
        scope.cancel()
    }
}
