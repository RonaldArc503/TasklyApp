package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ronaldcolocho.taskly.domain.model.*
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicTrackActionsViewModel @Inject constructor(
    private val auth: FirebaseAuth, private val music: IMusicRepository,
    private val downloads: MediaDownloadManager
) : ViewModel() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val downloadStates = downloads.states
    fun downloadState(id: String) = downloads.currentState(id)
    fun inspect(track: MusicTrack) { downloads.resolveLocalFile(track.id, MediaKind.AUDIO) }
    val playlists = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else music.observePlaylists(uid)
            .map { lists -> lists.filter { it.editable } }
            .catch { _notice.value = "No se pudieron cargar tus listas."; emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(track: MusicTrack, playlist: MusicPlaylist, onAdded: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (_busy.value || !playlist.editable || playlist.userId != uid) return
        _busy.value = true
        viewModelScope.launch {
            try {
                music.addTrackToPlaylist(uid, playlist.id, track)
                    .onSuccess { _notice.value = "Guardada en ${playlist.name}"; onAdded() }
                    .onFailure { _notice.value = it.message ?: "No se pudo agregar la canción." }
            } finally { _busy.value = false }
        }
    }
    fun download(track: MusicTrack) = downloadAll(listOf(track))
    fun downloadAll(tracks: List<MusicTrack>) {
        downloads.downloadTracks(tracks)
    }
    fun clearNotice() { _notice.value = null }
}
