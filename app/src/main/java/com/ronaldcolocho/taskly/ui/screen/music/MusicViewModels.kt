package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRepository: IMusicRepository,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel() {
    private val userId = getCurrentUserIdUseCase()
    val mostPlayed = musicRepository.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = userId?.let { musicRepository.observePlaylists(it) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createPlaylist(name: String, onCreated: (String) -> Unit) {
        val uid = userId ?: return
        viewModelScope.launch {
            musicRepository.createPlaylist(uid, name)
                .onSuccess(onCreated)
                .onFailure { _error.value = it.message ?: "No se pudo crear la lista." }
        }
    }

    fun clearError() { _error.value = null }

    fun renamePlaylist(id: String, name: String) {
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.renamePlaylist(uid, id, name).onFailure { _error.value = it.message ?: "No se pudo renombrar la lista." } }
    }
    fun deletePlaylist(id: String) {
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.deletePlaylist(uid, id).onFailure { _error.value = it.message ?: "No se pudo eliminar la lista." } }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val musicRepository: IMusicRepository,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel() {
    private val userId = getCurrentUserIdUseCase()
    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
    val selectedPlaylistId: String = playlistId
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _downloadedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<MusicTrack>> = _downloadedTracks.asStateFlow()
    val tracks = userId?.let { musicRepository.observePlaylistTracks(it, playlistId) }
        ?.onEach { tracks ->
            val uid = userId ?: return@onEach
            runCatching { musicRepository.syncPlaylistSongCount(uid, playlistId, tracks.size) }
                .onFailure { error -> _error.value = "No se pudo actualizar el contador: ${error.message}" }
        }
        ?.catch { error ->
            _error.value = "No se pudieron cargar las canciones: ${error.message ?: "error de Firestore"}"
            emit(emptyList())
        }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())
    val playlists = userId?.let { musicRepository.observePlaylists(it) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())
    val mostPlayed = musicRepository.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playlist(): MusicPlaylist? = playlists.value.firstOrNull { it.id == playlistId }

    fun addTrack(track: MusicTrack) {
        val uid = userId ?: return
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(uid, playlistId, track)
                .onFailure { _error.value = it.message ?: "No se pudo agregar la cancion." }
        }
    }

    fun loadDownloadedTracks() {
        viewModelScope.launch {
            runCatching { musicRepository.downloadedAudioTracks() }
                .onSuccess { _downloadedTracks.value = it }
                .onFailure { _error.value = it.message ?: "No se pudieron cargar los audios descargados." }
        }
    }

    fun clearError() { _error.value = null }

    fun removeTrack(track: MusicTrack) {
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.removeTrackFromPlaylist(uid, playlistId, track.id).onFailure { _error.value = it.message ?: "No se pudo quitar la canción." } }
    }
    fun reorderTracks(tracks: List<MusicTrack>) {
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.reorderTracks(uid, playlistId, tracks.map(MusicTrack::id)).onFailure { _error.value = it.message ?: "No se pudo guardar el orden." } }
    }
}
