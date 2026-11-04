package com.ronaldcolocho.taskly.ui.screen.music

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.ronaldcolocho.taskly.data.repository.SharedMusicPlaylistRepository
import com.ronaldcolocho.taskly.domain.model.*
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicPlaylistActionsViewModel @Inject constructor(
    private val auth: FirebaseAuth, private val music: IMusicRepository,
    private val shared: SharedMusicPlaylistRepository, private val downloads: MediaDownloadManager
) : ViewModel() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()

    private fun perform(id: String, action: suspend (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (id in _busy.value) return
        _busy.update { it + id }
        viewModelScope.launch {
            try { action(uid) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _notice.value = error.message ?: "No se pudo completar la operación." }
            finally { _busy.update { it - id } }
        }
    }
    fun share(list: MusicPlaylist, onLink: (String) -> Unit) = perform(list.id) { uid -> onLink(shared.share(uid, list)) }
    fun download(list: MusicPlaylist) = perform(list.id) { uid ->
        val tracks = music.observePlaylistTracks(
            list.sharedOwnerId.ifBlank { uid }, list.sharedPlaylistId.ifBlank { list.id }).first()
        downloads.downloadTracks(tracks)
        _notice.value = if (tracks.isEmpty()) "La lista está vacía." else "Descargas en cola. El progreso aparece sobre la portada de cada canción."
    }
    fun clearNotice() { _notice.value = null }
}

internal fun shareMusicPlaylist(context: Context, link: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }, "Compartir lista"))
}

@Composable
internal fun MusicPlaylistActionItems(list: MusicPlaylist, actions: MusicPlaylistActionsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val busy by actions.busy.collectAsStateWithLifecycle()
    DropdownMenuItem(text = { Text("Compartir lista") }, enabled = list.id !in busy, onClick = {
        actions.share(list) { shareMusicPlaylist(context, it) }; onDismiss()
    })
    DropdownMenuItem(text = { Text("Descargar / Offline") }, enabled = list.id !in busy, onClick = {
        actions.download(list); onDismiss()
    })
}

@Composable
internal fun MusicPlaylistMenu(list: MusicPlaylist?, actions: MusicPlaylistActionsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = list != null, onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Opciones de lista") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            list?.let { MusicPlaylistActionItems(it, actions) { expanded = false } }
        }
    }
}

@Composable
internal fun MusicActionNotice(message: String?, onDismiss: () -> Unit) {
    message ?: return
    AlertDialog(onDismissRequest = onDismiss, text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Aceptar") } })
}
