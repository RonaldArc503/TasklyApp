package com.ronaldcolocho.taskly.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: IMusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var trackResolver: ((AudioTrack) -> File?)? = null
    private var ensureTrack: (suspend (AudioTrack) -> File?)? = null

    private val prefs = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
    
    private val _state = MutableStateFlow(
        AudioPlayerState(
            mode = AudioMode.valueOf(prefs.getString("audio_mode", AudioMode.INDIVIDUAL.name) ?: AudioMode.INDIVIDUAL.name),
            repeatMode = AudioRepeatMode.valueOf(
                prefs.getString("audio_repeat_mode", AudioRepeatMode.OFF.name) ?: AudioRepeatMode.OFF.name
            )
        )
    )
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    /** Modo de reproducción: cambia con poca frecuencia (solo al cambiar de modo). */
    val modeState: StateFlow<AudioMode> = _state.map { it.mode }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, _state.value.mode)

    /** Flujos acotados por track: solo emiten cuando `trackId` es el track activo. */
    fun isCurrentFlow(trackId: String): Flow<Boolean> =
        _state.map { it.currentTrack?.id == trackId }.distinctUntilChanged()

    fun playingFor(trackId: String): Flow<Boolean> =
        _state.map { it.playing && it.currentTrack?.id == trackId }.distinctUntilChanged()

    fun timeFor(trackId: String): Flow<Long> =
        _state.map { if (it.currentTrack?.id == trackId) it.currentTimeMs else 0L }.distinctUntilChanged()

    fun durationFor(trackId: String): Flow<Long> =
        _state.map { if (it.currentTrack?.id == trackId) it.durationMs else 0L }.distinctUntilChanged()

    private var player: Player? = null
    private var connectionStarted = false
    private val pendingActions = mutableListOf<(Player) -> Unit>()

    private fun ensurePlayerConnection() {
        if (connectionStarted) return
        connectionStarted = true
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val p = controllerFuture.get()
                player = p
                setupPlayerListener(p)
                syncStateWithPlayer(p)
                
                pendingActions.forEach { it(p) }
                pendingActions.clear()
            } catch (e: Exception) {
                connectionStarted = false
                android.util.Log.e("AudioPlayer", "Failed to connect to MediaSession", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun withPlayer(action: (Player) -> Unit) {
        val p = player
        if (p != null) {
            action(p)
        } else {
            pendingActions.add(action)
            ensurePlayerConnection()
        }
    }

    private fun setupPlayerListener(p: Player) {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (isPlaying) {
                    startTicker()
                    _state.value.currentTrack?.let(::recordPlayback)
                } else stopTicker()
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(playing = false, error = "No se pudo reproducir el audio. Reintenta o verifica tu conexión.") }
                android.util.Log.e("AudioPlayer", "Playback error", error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId
                val newTrack = _state.value.tracks.find { it.id == trackId }
                if (newTrack != null) {
                    _state.update { it.copy(currentTrack = newTrack) }
                }
                
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.mode == AudioMode.INDIVIDUAL) {
                    p.pause()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val dur = p.duration.takeIf { it > 0 } ?: 0
                        _state.update { it.copy(durationMs = dur) }
                    }
                    Player.STATE_ENDED -> {
                        _state.update { it.copy(playing = false) }
                        stopTicker()
                    }
                }
            }
        })
    }

    private fun syncStateWithPlayer(p: Player) {
        val currentId = p.currentMediaItem?.mediaId
        val track = _state.value.tracks.find { it.id == currentId }
        val isPlaying = p.isPlaying
        _state.update {
            it.copy(
                currentTrack = track,
                playing = isPlaying,
                currentTimeMs = p.currentPosition.takeIf { pos -> pos >= 0 } ?: 0,
                durationMs = p.duration.takeIf { dur -> dur > 0 } ?: 0
            )
        }
        if (isPlaying) startTicker()
    }

    private fun AudioRepeatMode.toPlayerRepeatMode(): Int = when (this) {
        AudioRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        AudioRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        AudioRepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        val file = trackResolver?.invoke(this)
        val localFile = file?.takeIf { it.exists() }
        val uri = if (localFile != null) {
            Log.d("AudioPlayer", "CACHE HIT: track=$id")
            Uri.fromFile(localFile)
        } else {
            Log.d("AudioPlayer", "REMOTE FALLBACK: track=$id")
            Uri.parse(this.url)
        }
        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.name)
                    .setArtist("Taskly")
                    .build()
            )
            .build()
    }

    fun setTrackResolver(resolver: (AudioTrack) -> File?) {
        trackResolver = resolver
    }

    fun setEnsureTrack(resolver: suspend (AudioTrack) -> File?) {
        ensureTrack = resolver
    }

    fun updatePlaylist(tracks: List<AudioTrack>) {
        _state.update { it.copy(tracks = tracks) }
    }

    /** Edits only the in-memory global queue; it never mutates playlist documents. */
    fun reorderQueue(tracks: List<AudioTrack>) {
        _state.update { it.copy(tracks = tracks) }
        withPlayer { player ->
            val currentId = _state.value.currentTrack?.id
            player.setMediaItems(tracks.map { it.toMediaItem() })
            tracks.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { player.seekToDefaultPosition(it) }
            player.prepare()
        }
    }

    fun removeFromQueue(trackId: String) = reorderQueue(_state.value.tracks.filterNot { it.id == trackId })
    fun skipToQueueItem(trackId: String) {
        val index = _state.value.tracks.indexOfFirst { it.id == trackId }
        if (index >= 0) withPlayer { it.seekToDefaultPosition(index); it.play() }
    }

    private fun applyPlaylistAndPlay(track: AudioTrack) {
        val localAvailable = trackResolver?.invoke(track)?.exists() == true
        if (!localAvailable && track.url.isBlank()) {
            _state.update { it.copy(playing = false, error = "Audio no disponible") }
            return
        }
        withPlayer { p ->
            val s = _state.value

            val items = s.tracks.map { it.toMediaItem() }
            p.setMediaItems(items)

            p.repeatMode = s.repeatMode.toPlayerRepeatMode()

            val idx = s.tracks.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                p.seekToDefaultPosition(idx)
            }
            
            _state.update {
                it.copy(currentTrack = track, currentTimeMs = 0, durationMs = 0, playing = true, error = null)
            }
            p.prepare()
            p.play()
        }
    }

    fun playWithIndividual(track: AudioTrack) {
        updateMode(AudioMode.INDIVIDUAL)
        applyPlaylistAndPlay(track)
    }

    fun playWithQueue(track: AudioTrack) {
        _state.update { it.copy(mode = AudioMode.QUEUE) }
        applyPlaylistAndPlay(track)
    }

    fun playWithRepeat(track: AudioTrack) {
        updateMode(AudioMode.REPEAT)
        applyPlaylistAndPlay(track)
    }

    fun playWithLoop(track: AudioTrack) {
        updateMode(AudioMode.LOOP)
        applyPlaylistAndPlay(track)
    }

    fun toggleTrack(track: AudioTrack) {
        if (_state.value.currentTrack?.id == track.id) toggleCurrent() else applyPlaylistAndPlay(track)
    }

    fun toggleCurrent() {
        withPlayer { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun retryCurrent() { _state.value.currentTrack?.let(::applyPlaylistAndPlay) }
    fun clearError() { _state.update { it.copy(error = null) } }

    fun updateMode(mode: AudioMode) {
        prefs.edit().putString("audio_mode", mode.name).apply()
        val repeatMode = when (mode) {
            AudioMode.REPEAT -> AudioRepeatMode.ONE
            AudioMode.LOOP -> AudioRepeatMode.ALL
            else -> AudioRepeatMode.OFF
        }
        setRepeatMode(repeatMode, mode)
    }

    fun cycleRepeatMode() = setRepeatMode(_state.value.repeatMode.next())

    fun setRepeatMode(repeatMode: AudioRepeatMode, mode: AudioMode? = null) {
        prefs.edit().putString("audio_repeat_mode", repeatMode.name).apply()
        _state.update { it.copy(repeatMode = repeatMode, mode = mode ?: it.mode) }
        withPlayer { it.repeatMode = repeatMode.toPlayerRepeatMode() }
    }

    fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0L) return
        sleepTimerJob?.cancel()
        val endAt = System.currentTimeMillis() + durationMs
        _state.update { it.copy(sleepTimerEndAt = endAt) }
        sleepTimerJob = scope.launch {
            delay(durationMs)
            withPlayer { it.pause() }
            _state.update { it.copy(playing = false, sleepTimerEndAt = null) }
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimerEndAt = null) }
    }

    fun next() {
        withPlayer { p ->
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
            }
        }
    }

    fun prev() {
        withPlayer { p ->
            if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
            }
        }
    }

    fun seek(ms: Long) {
        withPlayer { p ->
            p.seekTo(ms)
            _state.update { it.copy(currentTimeMs = ms) }
        }
    }

    fun stop() {
        cancelSleepTimer()
        withPlayer { p ->
            p.stop()
            p.clearMediaItems()
        }
        _state.update {
            it.copy(currentTrack = null, currentTimeMs = 0, durationMs = 0, playing = false)
        }
        stopTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                withPlayer { p ->
                    val pos = p.currentPosition.takeIf { it >= 0 } ?: 0
                    _state.update { it.copy(currentTimeMs = pos) }
                }
                delay(500)
            }
        }
    }

    private fun recordPlayback(track: AudioTrack) {
        scope.launch(Dispatchers.IO) {
            musicRepository.recordPlayback(
                MusicTrack(
                    id = track.id,
                    url = track.url,
                    name = track.name,
                    sourceMessageId = track.msgId,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl
                )
            )
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun release() {
        cancelSleepTimer()
        withPlayer { p ->
            p.release()
        }
        stopTicker()
        scope.cancel()
    }
}
