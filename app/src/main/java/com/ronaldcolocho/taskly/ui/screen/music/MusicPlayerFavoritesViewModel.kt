package com.ronaldcolocho.taskly.ui.screen.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ronaldcolocho.taskly.audio.AudioTrack
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PlayerFavoriteSnapshot(val ids: Set<String> = emptySet(), val ready: Boolean = false, val userId: String? = null)
data class PlayerFavoriteNotice(val message: String, val trackId: String? = null, val favorite: Boolean? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicPlayerFavoritesViewModel internal constructor(
    private val music: IMusicRepository, account: Flow<String?>, initialUser: String?
) : ViewModel() {
    @Inject constructor(music: IMusicRepository, auth: FirebaseAuth) : this(music, callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }, auth.currentUser?.uid)

    private val user = account.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, initialUser)
    private val _notice = MutableStateFlow<PlayerFavoriteNotice?>(null)
    val notice = _notice.asStateFlow()
    private val _saving = MutableStateFlow<String?>(null)
    val saving = _saving.asStateFlow()
    private var mutation: Job? = null

    val favorites = user.flatMapLatest { uid ->
        if (uid == null) flowOf(PlayerFavoriteSnapshot(ready = true))
        else music.observeFavorites(uid).map { PlayerFavoriteSnapshot(it.map(MusicTrack::id).toSet(), true, uid) }
            .onStart { emit(PlayerFavoriteSnapshot(userId = uid)) }
            .catch { error ->
                if (error is CancellationException) throw error
                _notice.value = PlayerFavoriteNotice("No se pudieron cargar tus favoritas.")
                emit(PlayerFavoriteSnapshot(userId = uid))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerFavoriteSnapshot())

    init {
        viewModelScope.launch { user.drop(1).collect {
            mutation?.cancel(); _saving.value = null; _notice.value = null
        } }
    }

    fun toggle(track: AudioTrack?) {
        track ?: return
        val uid = user.value ?: run { _notice.value = PlayerFavoriteNotice("Inicia sesión para guardar favoritas."); return }
        if (mutation?.isActive == true) return
        val snapshot = favorites.value
        if (!snapshot.ready || snapshot.userId != uid) {
            _notice.value = PlayerFavoriteNotice("Espera a que se carguen tus favoritas."); return
        }
        val favorite = track.id !in snapshot.ids
        _saving.value = track.id
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val song = MusicTrack(track.id, track.url, track.name, track.msgId, track.durationSeconds,
                    track.thumbnailUrl, artist = track.artist, sourceChannel = track.sourceChannel)
                music.setFavorite(uid, song, favorite).getOrThrow()
                if (user.value == uid) _notice.value = PlayerFavoriteNotice(
                    "${if (favorite) "Guardada en favoritas" else "Quitada de favoritas"} · ${track.name}", track.id, favorite)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _notice.value = PlayerFavoriteNotice("No se pudo actualizar el favorito. Vuelve a intentarlo.") }
            finally { if (mutation === currentCoroutineContext().job) _saving.value = null }
        }
        mutation = job
        job.start()
    }

    fun clearNotice() { _notice.value = null }
}
