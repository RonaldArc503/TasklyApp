package com.ronaldcolocho.taskly.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import com.ronaldcolocho.taskly.domain.model.MediaKind
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.util.DownloadedSongResolver
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
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: IMusicRepository,
    private val downloadHistoryRepository: IDownloadHistoryRepository,
    private val auth: FirebaseAuth,
    private val mediaCache: IMediaCacheRepository,
    private val playerColors: PlayerColorsController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val enqueuedTrackIds = mutableListOf<String>()

    private var cachedDownloadedSongs: List<DownloadedSong> = emptyList()

    init {
        scope.launch {
            downloadHistoryRepository.getDownloadedSongs().collect { songs ->
                cachedDownloadedSongs = songs
            }
        }
    }

    private var trackResolver: ((AudioTrack) -> File?)? = null
    private var ensureTrack: (suspend (AudioTrack) -> File?)? = null

    private val prefs = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
    private val sessionWrites = Channel<Pair<String, AudioSessionSnapshot>>(Channel.UNLIMITED)
    private val sessionWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionUserId = auth.currentUser?.uid

    private fun savedSession(userId: String?) = userId?.let {
        AudioSessionCodec.decode(prefs.getString("music_session_$it", null))
    }
    
    private val _state = MutableStateFlow(
        savedSession(sessionUserId)?.toState() ?: AudioPlayerState(
            mode = AudioMode.valueOf(prefs.getString("audio_mode", AudioMode.INDIVIDUAL.name) ?: AudioMode.INDIVIDUAL.name),
            repeatMode = AudioRepeatMode.valueOf(
                prefs.getString("audio_repeat_mode", AudioRepeatMode.OFF.name) ?: AudioRepeatMode.OFF.name
            ),
            shuffleModeEnabled = prefs.getBoolean("audio_shuffle_mode", false)
        )
    )
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val _visualEffect = MutableStateFlow(
        PlayerVisualEffect.fromId(prefs.getString("player_visual_effect", PlayerVisualEffect.MAREA.id))
    )
    val visualEffect: StateFlow<PlayerVisualEffect> = _visualEffect.asStateFlow()

    fun setVisualEffect(effect: PlayerVisualEffect) {
        _visualEffect.value = effect
        prefs.edit().putString("player_visual_effect", effect.id).apply()
    }

    val colorState: StateFlow<PlayerColorState> = playerColors.state

    init {
        playerColors.onTrackChanged(_state.value.currentTrack?.id)
        scope.launch {
            _state.map { it.currentTrack?.id }.distinctUntilChanged().collect(playerColors::onTrackChanged)
        }
    }

    fun setColorMode(mode: PlayerColorMode) = playerColors.setMode(mode)
    fun changeColor(): PlayerColorState = playerColors.changeColor()

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
    private val authListener = FirebaseAuth.AuthStateListener { firebase ->
        val uid = firebase.currentUser?.uid
        if (uid != sessionUserId) {
            persistSession()
            sessionUserId = uid
            pendingActions.clear()
            stopTicker()
            cancelSleepTimer()
            enqueuedTrackIds.clear()
            player?.pause()
            player?.clearMediaItems()
            _state.value = savedSession(uid)?.toState() ?: AudioPlayerState()
            if (_state.value.currentTrack != null) withPlayer(::restoreSession)
        }
    }

    init {
        sessionWriteScope.launch {
            for ((uid, snapshot) in sessionWrites) {
                val editor = prefs.edit()
                if (snapshot.currentId == null) editor.remove("music_session_$uid")
                else editor.putString("music_session_$uid", AudioSessionCodec.encode(snapshot))
                editor.apply()
            }
        }
        auth.addAuthStateListener(authListener)
        scope.launch {
            // Position checkpoints every five seconds, plus every queue/mode/pause change.
            _state.map { state ->
                val snapshot = AudioSessionSnapshot.from(state)
                snapshot.copy(positionMs = snapshot.positionMs / 5_000) to state.playing
            }.distinctUntilChanged().collect { persistSession() }
        }
        if (_state.value.currentTrack != null) ensurePlayerConnection()
    }

    private fun persistSession() {
        val uid = sessionUserId ?: return
        val snapshot = AudioSessionSnapshot.from(_state.value)
        sessionWrites.trySend(uid to snapshot)
    }

    private fun restoreSession(p: Player) {
        val snapshot = _state.value
        val index = snapshot.tracks.indexOfFirst { it.id == snapshot.currentTrack?.id }
        if (index < 0) return
        p.playWhenReady = false
        p.setMediaItems(snapshot.tracks.map { it.toMediaItem() }, index, snapshot.currentTimeMs)
        p.repeatMode = snapshot.repeatMode.toPlayerRepeatMode()
        p.shuffleModeEnabled = snapshot.shuffleModeEnabled
        p.prepare()
    }

    private fun ensurePlayerConnection() {
        if (connectionStarted) return
        connectionStarted = true
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val p = controllerFuture.get()
                player = p
                if (p.mediaItemCount > 0) {
                    val known = _state.value.tracks.associateBy { it.id }
                    val liveTracks = (0 until p.mediaItemCount).map { index ->
                        val item = p.getMediaItemAt(index)
                        known[item.mediaId] ?: AudioTrack(item.mediaId,
                            item.mediaMetadata.extras?.getString("originalUrl")
                                ?: item.localConfiguration?.uri?.toString().orEmpty(),
                            item.mediaMetadata.title?.toString().orEmpty(),
                            item.mediaMetadata.extras?.getString("messageId").orEmpty(),
                            thumbnailUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
                            playlistId = item.mediaMetadata.extras?.getString("playlistId"),
                            artist = item.mediaMetadata.artist?.toString().orEmpty())
                    }
                    _state.update { it.copy(tracks = liveTracks, shuffleModeEnabled = p.shuffleModeEnabled,
                        repeatMode = when (p.repeatMode) {
                            Player.REPEAT_MODE_ALL -> AudioRepeatMode.ALL
                            Player.REPEAT_MODE_ONE -> AudioRepeatMode.ONE
                            else -> AudioRepeatMode.OFF
                        }) }
                } else restoreSession(p)
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
                _state.update { it.copy(playing = isPlaying, error = if (isPlaying) null else it.error) }
                if (isPlaying) {
                    startTicker()
                    _state.value.currentTrack?.let(::recordPlayback)
                } else stopTicker()
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(playing = false, buffering = false,
                    error = "No se pudo reproducir el audio. Reintenta o verifica tu conexión.") }
                android.util.Log.e("AudioPlayer", "Playback error", error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId
                val newTrack = _state.value.tracks.find { it.id == trackId }
                val pos = p.currentPosition.takeIf { it >= 0 } ?: 0L
                val dur = p.duration.takeIf { it > 0 }
                    ?: (newTrack?.durationSeconds?.takeIf { it > 0 }?.toLong()?.times(1000L))
                    ?: 0L

                _state.update {
                    it.copy(
                        currentTrack = newTrack ?: it.currentTrack,
                        currentTimeMs = pos,
                        durationMs = dur,
                        playing = p.isPlaying
                    )
                }
                if (trackId != null) {
                    enqueuedTrackIds.remove(trackId)
                }
                
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.mode == AudioMode.INDIVIDUAL) {
                    p.pause()
                } else if (p.isPlaying) {
                    startTicker()
                    newTrack?.let(::recordPlayback)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val pos = newPosition.positionMs.takeIf { it >= 0 }
                    ?: p.currentPosition.takeIf { it >= 0 }
                    ?: 0L
                val dur = p.duration.takeIf { it > 0 }
                    ?: _state.value.currentTrack?.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L }
                    ?: _state.value.durationMs

                _state.update {
                    it.copy(
                        currentTimeMs = pos,
                        durationMs = dur,
                        playing = p.isPlaying
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                when (playbackState) {
                    Player.STATE_READY -> {
                        val dur = p.duration.takeIf { it > 0 }
                            ?: _state.value.currentTrack?.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L }
                            ?: 0L
                        val pos = p.currentPosition.takeIf { it >= 0 } ?: 0L
                        _state.update { it.copy(durationMs = dur, currentTimeMs = pos, playing = p.isPlaying) }
                        if (p.isPlaying) startTicker()
                    }
                    Player.STATE_ENDED -> {
                        if (!p.playWhenReady) {
                            _state.update { it.copy(playing = false) }
                            stopTicker()
                            return
                        }
                        if (_state.value.repeatMode == AudioRepeatMode.ALL && _state.value.tracks.isNotEmpty()) {
                            val firstIdx = p.currentTimeline.takeUnless { it.isEmpty }
                                ?.getFirstWindowIndex(p.shuffleModeEnabled)
                                ?.takeIf { it != -1 } ?: 0
                            p.seekToDefaultPosition(firstIdx)
                            p.play()
                        } else if (_state.value.repeatMode == AudioRepeatMode.ONE && _state.value.tracks.isNotEmpty()) {
                            p.seekTo(0L)
                            p.play()
                        } else {
                            _state.update { it.copy(playing = false) }
                            stopTicker()
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        val dur = p.duration.takeIf { it > 0 }
                            ?: _state.value.currentTrack?.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L }
                            ?: _state.value.durationMs
                        _state.update { it.copy(durationMs = dur) }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mode = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> AudioRepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> AudioRepeatMode.ONE
                    else -> AudioRepeatMode.OFF
                }
                _state.update { it.copy(repeatMode = mode) }
            }
        })
    }

    private fun syncStateWithPlayer(p: Player) {
        val currentId = p.currentMediaItem?.mediaId
        val track = _state.value.tracks.find { it.id == currentId }
        val isPlaying = p.isPlaying
        val dur = p.duration.takeIf { it > 0 }
            ?: track?.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L }
            ?: 0L
        _state.update {
            it.copy(
                currentTrack = track,
                playing = isPlaying,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                currentTimeMs = p.currentPosition.takeIf { pos -> pos >= 0 } ?: 0L,
                durationMs = dur
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
            ?: mediaCache.fileFor(id, MediaKind.AUDIO)
            ?: DownloadedSongResolver.resolveTrackFile(
                context = context,
                trackUrl = this.url,
                trackName = this.name,
                trackId = this.id,
                downloadedSongs = cachedDownloadedSongs
            )
        val localFile = file?.takeIf { it.isFile && it.length() > 0 }
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
            .setCustomCacheKey(OnlineMusicSource.cacheKey(this.url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.name)
                    .setArtist(artist.ifBlank { "Taskly" })
                    .setArtworkUri(thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setExtras(Bundle().apply {
                        putString("messageId", msgId); putString("playlistId", playlistId); putString("originalUrl", url)
                    })
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
        prefs.edit().putString("current_playlist_id", tracks.firstOrNull()?.playlistId).apply()
    }

    /** Queue edits preserve the active item and position; saved playlists are untouched. */
    fun reorderQueue(tracks: List<AudioTrack>) {
        if (tracks == _state.value.tracks) return
        enqueuedTrackIds.clear()
        withPlayer { p ->
            // An explicit queue order takes precedence over random traversal.
            if (_state.value.shuffleModeEnabled) setShuffleMode(false)
            val previous = _state.value
            val edit = planAudioQueueEdit(previous.tracks, previous.currentTrack?.id,
                p.currentPosition.coerceAtLeast(0), tracks)
            val playWhenReady = p.playWhenReady
            if (edit.tracks.isEmpty()) {
                p.pause()
                p.clearMediaItems()
                _state.update { it.copy(tracks = emptyList(), currentTrack = null, currentTimeMs = 0,
                    durationMs = 0, playing = false) }
            } else {
                _state.update { it.copy(tracks = edit.tracks, currentTrack = edit.tracks[edit.currentIndex],
                    currentTimeMs = edit.positionMs) }
                val liveIds = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
                if (liveIds.toSet() == edit.tracks.map { it.id }.toSet() && liveIds.size == edit.tracks.size) {
                    // Moving items keeps the decoder and current position intact.
                    edit.tracks.forEachIndexed { target, track ->
                        val from = (0 until p.mediaItemCount).first { p.getMediaItemAt(it).mediaId == track.id }
                        if (from != target) p.moveMediaItem(from, target)
                    }
                } else {
                    p.setMediaItems(edit.tracks.map { it.toMediaItem() }, edit.currentIndex, edit.positionMs)
                    p.prepare()
                }
                p.playWhenReady = playWhenReady
            }
            persistSession()
        }
    }

    fun moveQueueItem(trackId: String, targetIndex: Int) {
        val tracks = _state.value.tracks.toMutableList()
        val from = tracks.indexOfFirst { it.id == trackId }
        if (from < 0) return
        val track = tracks.removeAt(from)
        tracks.add(targetIndex.coerceIn(0, tracks.size), track)
        reorderQueue(tracks)
    }

    fun addToQueueEnd(track: AudioTrack): QueueResult {
        val current = _state.value
        if (current.currentTrack == null) {
            updatePlaylist(listOf(track))
            playWithQueue(track)
            return QueueResult.PLAYING_NOW
        }
        if (current.currentTrack.id == track.id) return QueueResult.ALREADY_PLAYING
        if (current.mode == AudioMode.INDIVIDUAL) updateMode(AudioMode.QUEUE)
        reorderQueue(current.tracks.filterNot { it.id == track.id } + track)
        return QueueResult.ADDED_TO_QUEUE
    }

    fun removeFromQueue(trackId: String) = reorderQueue(_state.value.tracks.filterNot { it.id == trackId })
    fun skipToQueueItem(trackId: String) {
        val index = _state.value.tracks.indexOfFirst { it.id == trackId }
        if (index >= 0) withPlayer { it.seekToDefaultPosition(index); it.play() }
    }

    /**
     * Inserts [track] into the playback queue right after the currently playing track.
     * If other tracks have already been enqueued recently, this track is inserted
     * immediately after the last enqueued track (FIFO order: Current -> Queue 1 -> Queue 2 ...).
     */
    fun addToQueueNext(track: AudioTrack, playlistFallback: List<AudioTrack> = emptyList()): QueueResult {
        val currentState = _state.value
        val currentTrack = currentState.currentTrack

        // 1. If nothing is currently loaded or playing
        if (currentTrack == null || (!currentState.playing && currentState.tracks.isEmpty())) {
            val tracksToUse = if (currentState.tracks.isNotEmpty()) {
                currentState.tracks
            } else if (playlistFallback.isNotEmpty()) {
                playlistFallback
            } else {
                listOf(track)
            }
            updatePlaylist(tracksToUse)
            playWithQueue(track)
            return QueueResult.PLAYING_NOW
        }

        // 2. If the swiped track is already playing
        if (currentTrack.id == track.id) {
            return QueueResult.ALREADY_PLAYING
        }

        // Explicit "next" entries must follow the displayed queue, even after shuffle.
        if (currentState.shuffleModeEnabled) setShuffleMode(false)

        // 3. Ensure player is in continuous queue mode so it won't pause after current track
        if (currentState.mode == AudioMode.INDIVIDUAL) {
            updateMode(AudioMode.QUEUE)
        }

        val currentTracks = currentState.tracks.toMutableList()
        val currentTrackIndex = currentTracks.indexOfFirst { it.id == currentTrack.id }

        // If currentTrack is somehow not found in tracks list, append track and add to player
        if (currentTrackIndex == -1) {
            currentTracks.add(track)
            updatePlaylist(currentTracks)
            withPlayer { p ->
                p.addMediaItem(track.toMediaItem())
            }
            enqueuedTrackIds.add(track.id)
            return QueueResult.ADDED_AS_NEXT
        }

        // Prune any enqueued IDs that are no longer in tracks or have already passed (positioned <= currentTrackIndex)
        enqueuedTrackIds.removeAll { id ->
            val idx = currentTracks.indexOfFirst { it.id == id }
            idx == -1 || idx <= currentTrackIndex
        }

        // Determine anchor: either the last enqueued track still ahead in queue, or currentTrack
        val lastQueuedId = enqueuedTrackIds.lastOrNull()
        val isSubsequentInQueue = lastQueuedId != null
        val anchorTrackId = lastQueuedId ?: currentTrack.id

        val oldIndex = currentTracks.indexOfFirst { it.id == track.id }
        val currentAnchorIndex = currentTracks.indexOfFirst { it.id == anchorTrackId }

        // If the track is already immediately after the anchor track:
        if (oldIndex != -1 && oldIndex == currentAnchorIndex + 1) {
            if (!enqueuedTrackIds.contains(track.id)) {
                enqueuedTrackIds.add(track.id)
            }
            return if (isSubsequentInQueue) QueueResult.ADDED_TO_QUEUE else QueueResult.ADDED_AS_NEXT
        }

        val trackToInsert = if (oldIndex != -1) {
            currentTracks.removeAt(oldIndex)
        } else {
            track
        }

        // Re-find anchor index after removal
        val updatedAnchorIndex = currentTracks.indexOfFirst { it.id == anchorTrackId }.takeIf { it != -1 } ?: currentTrackIndex
        val targetIndex = (updatedAnchorIndex + 1).coerceIn(0, currentTracks.size)
        currentTracks.add(targetIndex, trackToInsert)

        // Update in-memory playlist state
        _state.update { it.copy(tracks = currentTracks) }

        // Update ExoPlayer
        withPlayer { p ->
            try {
                if (oldIndex != -1) {
                    if (oldIndex in 0 until p.mediaItemCount && targetIndex in 0..p.mediaItemCount) {
                        p.moveMediaItem(oldIndex, targetIndex)
                    } else {
                        val currentPos = p.currentPosition
                        val currentIdx = p.currentMediaItemIndex
                        p.setMediaItems(currentTracks.map { it.toMediaItem() }, currentIdx, currentPos)
                    }
                } else {
                    if (targetIndex in 0..p.mediaItemCount) {
                        p.addMediaItem(targetIndex, trackToInsert.toMediaItem())
                    } else {
                        p.addMediaItem(trackToInsert.toMediaItem())
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error updating media items for queue", e)
            }
        }

        enqueuedTrackIds.remove(track.id)
        enqueuedTrackIds.add(track.id)

        return if (isSubsequentInQueue) QueueResult.ADDED_TO_QUEUE else QueueResult.ADDED_AS_NEXT
    }

    private fun applyPlaylistAndPlay(track: AudioTrack) {
        val resolvedLocal = trackResolver?.invoke(track)
            ?: mediaCache.fileFor(track.id, MediaKind.AUDIO)
            ?: DownloadedSongResolver.resolveTrackFile(
                context = context,
                trackUrl = track.url,
                trackName = track.name,
                trackId = track.id,
                downloadedSongs = cachedDownloadedSongs
            )
        val localAvailable = resolvedLocal?.let { it.isFile && it.length() > 0 } == true
        val remoteAvailable = track.url.startsWith("https://") || track.url.startsWith("http://") || track.url.startsWith("content://")
        if (!localAvailable && !remoteAvailable) {
            _state.update { it.copy(playing = false, error = "Audio no disponible") }
            return
        }
        enqueuedTrackIds.clear()
        withPlayer { p ->
            val s = _state.value

            val items = s.tracks.map { it.toMediaItem() }
            p.setMediaItems(items)

            p.repeatMode = s.repeatMode.toPlayerRepeatMode()
            p.shuffleModeEnabled = s.shuffleModeEnabled

            val idx = s.tracks.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                p.seekToDefaultPosition(idx)
            }
            
            _state.update {
                it.copy(currentTrack = track, currentTimeMs = 0, durationMs = 0, playing = true, buffering = true, error = null)
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
        val targetMode = if (_state.value.repeatMode == AudioRepeatMode.ALL) AudioMode.LOOP else AudioMode.QUEUE
        _state.update { it.copy(mode = targetMode) }
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

    fun retryCurrent() {
        val snapshot = _state.value
        val track = snapshot.currentTrack ?: return
        withPlayer { p ->
            if (p.currentMediaItem?.mediaId != track.id) applyPlaylistAndPlay(track)
            else {
                _state.update { it.copy(error = null, buffering = true) }
                p.seekTo(p.currentMediaItemIndex, snapshot.currentTimeMs.coerceAtLeast(0))
                p.prepare()
                p.play()
            }
        }
    }
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
        val targetMode = mode ?: when (repeatMode) {
            AudioRepeatMode.ALL -> AudioMode.LOOP
            AudioRepeatMode.ONE -> AudioMode.REPEAT
            AudioRepeatMode.OFF -> AudioMode.QUEUE
        }
        prefs.edit().putString("audio_mode", targetMode.name).apply()
        _state.update { it.copy(repeatMode = repeatMode, mode = targetMode) }
        withPlayer {
            it.repeatMode = repeatMode.toPlayerRepeatMode()
            it.shuffleModeEnabled = _state.value.shuffleModeEnabled
        }
    }

    fun toggleShuffle() = setShuffleMode(!_state.value.shuffleModeEnabled)

    fun setShuffleMode(enabled: Boolean) {
        prefs.edit().putBoolean("audio_shuffle_mode", enabled).apply()
        if (enabled && _state.value.mode == AudioMode.INDIVIDUAL) {
            val targetMode = if (_state.value.repeatMode == AudioRepeatMode.ALL) AudioMode.LOOP else AudioMode.QUEUE
            _state.update { it.copy(mode = targetMode) }
            prefs.edit().putString("audio_mode", targetMode.name).apply()
        }
        _state.update { it.copy(shuffleModeEnabled = enabled) }
        withPlayer {
            it.shuffleModeEnabled = enabled
            it.repeatMode = _state.value.repeatMode.toPlayerRepeatMode()
        }
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
            _state.update { it.copy(currentTimeMs = 0L) }
            val timeline = p.currentTimeline
            if (!timeline.isEmpty) {
                val repeatModeForSeek = if (_state.value.repeatMode == AudioRepeatMode.OFF) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                val nextIdx = timeline.getNextWindowIndex(p.currentMediaItemIndex, repeatModeForSeek, p.shuffleModeEnabled)
                if (nextIdx != -1) {
                    p.seekToDefaultPosition(nextIdx)
                    p.play()
                    return@withPlayer
                }
            }
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
                p.play()
            } else if (p.mediaItemCount > 0) {
                val firstIdx = p.currentTimeline.takeUnless { it.isEmpty }
                    ?.getFirstWindowIndex(p.shuffleModeEnabled)
                    ?.takeIf { it != -1 } ?: 0
                p.seekToDefaultPosition(firstIdx)
                p.play()
            }
        }
    }

    fun prev(forceTrackChange: Boolean = false) {
        withPlayer { p ->
            if (!forceTrackChange && p.currentPosition > 3000L) {
                p.seekTo(0L)
                _state.update { it.copy(currentTimeMs = 0L) }
                return@withPlayer
            }
            _state.update { it.copy(currentTimeMs = 0L) }
            val timeline = p.currentTimeline
            if (!timeline.isEmpty) {
                val repeatModeForSeek = if (_state.value.repeatMode == AudioRepeatMode.OFF) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                val prevIdx = timeline.getPreviousWindowIndex(p.currentMediaItemIndex, repeatModeForSeek, p.shuffleModeEnabled)
                if (prevIdx != -1) {
                    p.seekToDefaultPosition(prevIdx)
                    p.play()
                    return@withPlayer
                }
            }
            if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
                p.play()
            } else if (p.mediaItemCount > 0) {
                val lastIdx = p.currentTimeline.takeUnless { it.isEmpty }
                    ?.getLastWindowIndex(p.shuffleModeEnabled)
                    ?.takeIf { it != -1 } ?: (p.mediaItemCount - 1)
                p.seekToDefaultPosition(lastIdx)
                p.play()
            }
        }
    }

    fun seek(ms: Long) {
        withPlayer { p ->
            p.seekTo(ms)
            _state.update { it.copy(currentTimeMs = ms) }
            persistSession()
        }
    }

    fun stop() {
        cancelSleepTimer()
        enqueuedTrackIds.clear()
        withPlayer { p ->
            p.stop()
            p.clearMediaItems()
        }
        _state.update {
            it.copy(tracks = emptyList(), currentTrack = null, currentTimeMs = 0, durationMs = 0,
                playing = false, buffering = false, error = null)
        }
        stopTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                withPlayer { p ->
                    val pos = p.currentPosition.takeIf { it >= 0 } ?: 0L
                    val dur = p.duration.takeIf { it > 0 }
                    _state.update { current ->
                        current.copy(
                            currentTimeMs = pos,
                            durationMs = if (dur != null && dur > 0) dur else current.durationMs
                        )
                    }
                }
                delay(300)
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
                    thumbnailUrl = track.thumbnailUrl,
                    artist = track.artist
                )
            )
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun release() {
        persistSession()
        sessionWrites.close()
        auth.removeAuthStateListener(authListener)
        cancelSleepTimer()
        withPlayer { p ->
            p.release()
        }
        stopTicker()
        scope.cancel()
    }
}
