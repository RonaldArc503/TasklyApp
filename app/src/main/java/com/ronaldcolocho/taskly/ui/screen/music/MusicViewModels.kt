package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.domain.usecase.profile.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.ronaldcolocho.taskly.data.repository.SharedMusicPlaylistRepository
import com.ronaldcolocho.taskly.domain.model.MusicPlaylistLink
import com.ronaldcolocho.taskly.domain.model.selectMusicPlaylist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val musicRepository: IMusicRepository,
    private val shared: SharedMusicPlaylistRepository,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel() {
    private val userId = getCurrentUserIdUseCase()
    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
    val selectedPlaylistId: String get() = selectedPlaylist.value?.id ?: playlistId
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _downloadedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<MusicTrack>> = _downloadedTracks.asStateFlow()
    val playlists = userId?.let { musicRepository.observePlaylists(it) }
        ?.catch { _error.value = "No se pudieron cargar las listas."; emit(emptyList()) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())
    private val shareId = playlistId.removePrefix("shared_").takeIf {
        playlistId.startsWith("shared_") && runCatching { MusicPlaylistLink.create(it) }.isSuccess
    }
    private val sharedUnavailable = "La lista compartida no está disponible o no tienes conexión."
    private val sharedPreview = playlists.map { lists -> lists.any { it.id == playlistId && it.editable } }
        .distinctUntilChanged().flatMapLatest { hasCopy ->
            if (hasCopy || shareId == null) flowOf(null) else shared.observe(shareId)
                .onEach { if (it == null) _error.value = sharedUnavailable }
                .catch { _error.value = sharedUnavailable; emit(null) }
        }
    val selectedPlaylist = combine(playlists, sharedPreview) { owned, original ->
        selectMusicPlaylist(owned, original, userId, playlistId)
    }.onEach { if (it?.editable == true && _error.value == sharedUnavailable) _error.value = null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tracks = selectedPlaylist.distinctUntilChanged { old, new ->
        old?.sharedOwnerId == new?.sharedOwnerId && old?.sharedPlaylistId == new?.sharedPlaylistId && old?.id == new?.id
    }.flatMapLatest { list ->
        if (list == null || userId == null) flowOf(emptyList())
        else musicRepository.observePlaylistTracks(list.sharedOwnerId.ifBlank { userId.orEmpty() },
            list.sharedPlaylistId.ifBlank { list.id })
            .onEach { tracks ->
                if (list.editable) runCatching { musicRepository.syncPlaylistSongCount(userId.orEmpty(), list.id, tracks.size) }
                    .onFailure { _error.value = "No se pudo actualizar el contador: ${it.message}" }
            }
    }.catch { _error.value = "No se pudieron cargar las canciones: ${it.message.orEmpty()}"; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved = combine(playlists, selectedPlaylist) { lists, list -> lists.any { it.id == playlistId && it.editable } || list?.sharedOwnerId == userId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    init {
        viewModelScope.launch {
            playlists.map { lists -> lists.firstOrNull { it.id == playlistId && !it.editable } }
                .filterNotNull().take(1).collect { copyShared(it) }
        }
    }

    fun saveShared() {
        selectedPlaylist.value?.let(::copyShared)
    }
    private fun copyShared(list: MusicPlaylist) {
        val uid = userId ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try { shared.save(uid, list); _notice.value = "Guardada en mi biblioteca" }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "No se pudo guardar la lista." }
            finally { _saving.value = false }
        }
    }
    val mostPlayed = musicRepository.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playlist(): MusicPlaylist? = selectedPlaylist.value

    fun addTrack(track: MusicTrack) {
        if (selectedPlaylist.value?.editable != true) return
        val uid = userId ?: return
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(uid, selectedPlaylistId, track)
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
        if (selectedPlaylist.value?.editable != true) return
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.removeTrackFromPlaylist(uid, selectedPlaylistId, track.id).onFailure { _error.value = it.message ?: "No se pudo quitar la canción." } }
    }
    fun reorderTracks(tracks: List<MusicTrack>) {
        if (selectedPlaylist.value?.editable != true) return
        val uid = userId ?: return
        viewModelScope.launch { musicRepository.reorderTracks(uid, selectedPlaylistId, tracks.map(MusicTrack::id)).onFailure { _error.value = it.message ?: "No se pudo guardar el orden." } }
    }
}
