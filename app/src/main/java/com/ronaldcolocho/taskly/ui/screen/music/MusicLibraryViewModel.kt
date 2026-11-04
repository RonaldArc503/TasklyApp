package com.ronaldcolocho.taskly.ui.screen.music

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ronaldcolocho.taskly.domain.model.*
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.util.DownloadedSongResolver
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class MusicLibraryState(val items: List<MusicLibraryItem> = emptyList(), val loading: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val music: IMusicRepository,
    private val cache: IMediaCacheRepository,
    private val downloads: IDownloadHistoryRepository,
    private val mediaDownloads: MediaDownloadManager
) : ViewModel() {
    private val refreshVersion = MutableStateFlow(0)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val downloadStates = mediaDownloads.states
    private val completedDownloads = downloadStates.map { states ->
        states.filterValues { it is MediaDownloadState.Downloaded || it is MediaDownloadState.Error }
    }.distinctUntilChanged()

    private val account = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val library = combine(account, refreshVersion) { uid, _ -> uid }.flatMapLatest { uid ->
        if (uid == null) flowOf(MusicLibraryState(loading = false))
        else {
            val favorites = music.observeFavorites(uid).catch {
                _error.value = "No se pudieron cargar los favoritos. Pulsa Actualizar para reintentar."
                emit(emptyList())
            }
            val playlistTracks = music.observePlaylists(uid).map { lists -> lists.map { it.id to it.name } }
                .distinctUntilChanged().flatMapLatest { lists ->
                    if (lists.isEmpty()) flowOf(emptyList<Pair<MusicTrack, String>>())
                    else combine(lists.map { (id, name) ->
                        music.observePlaylistTracks(uid, id).map { tracks -> tracks.map { it to name } }
                            .catch { _error.value = "No se pudieron cargar algunas listas."; emit(emptyList()) }
                    }) { it.flatMap { tracks -> tracks } }
                }.catch { _error.value = "No se pudieron cargar las listas."; emit(emptyList()) }
            combine(favorites, playlistTracks, music.observeMostPlayed(), downloads.getDownloadedSongs(), completedDownloads) {
                liked, tracks, history, downloaded, _ ->
                withContext(Dispatchers.IO) { buildLibrary(liked, tracks, history, downloaded) }
            }.catch { _error.value = "No se pudo revisar la biblioteca local."; emit(MusicLibraryState(loading = false)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicLibraryState())

    private suspend fun buildLibrary(
        favorites: List<MusicTrack>, playlistTracks: List<Pair<MusicTrack, String>>,
        history: List<MusicTrack>, downloaded: List<DownloadedSong>
    ): MusicLibraryState {
        val references = cache.downloadedAudioReferences(includeMissing = true)
        val localTracks = downloaded.map { song ->
            MusicTrack(song.id, android.net.Uri.fromFile(File(song.localPath)).toString(), song.title,
                sourceMessageId = song.savedMessageId.orEmpty(), durationSeconds = song.durationSeconds,
                thumbnailUrl = song.thumbnailUrl, addedAt = song.downloadedAt, artist = song.artist)
        }
        val cacheTracks = references.map {
            MusicTrack(it.mediaId, it.url, it.name, it.sourceMessageId, it.durationSeconds,
                addedAt = it.downloadedAt, artist = it.artist, thumbnailUrl = it.thumbnailUrl, sourceChannel = it.sourceChannel)
        }
        val sources = playlistTracks.map { it.first } + favorites + history + cacheTracks + localTracks
        val favoriteIds = favorites.mapTo(hashSetOf()) { it.id }
        val historyById = history.associateBy { it.id }
        val names = playlistTracks.groupBy({ it.first.id }, { it.second })
        val localById = downloaded.associateBy { it.id }
        val referenceIds = references.mapTo(hashSetOf()) { it.mediaId }
        val items = sources.groupBy { it.id }.map { (id, versions) ->
            val track = versions.firstOrNull { it.url.startsWith("http") } ?: versions.first()
            val song = localById[id]
            val file = cache.refreshFile(id, MediaKind.AUDIO)
                ?: song?.let { resolveLibraryFile(it) }
                ?: DownloadedSongResolver.resolveTrackFile(context, track.url, trackId = id)
            val stats = historyById[id]
            MusicLibraryItem(
                track.copy(playCount = stats?.playCount ?: track.playCount,
                    lastPlayedAt = stats?.lastPlayedAt ?: track.lastPlayedAt,
                    addedAt = versions.maxOf { it.addedAt }),
                favorite = id in favoriteIds, localPath = file?.absolutePath,
                sizeBytes = file?.length() ?: 0,
                wasDownloaded = id in referenceIds || song != null || track.url.startsWith("file:"),
                playlistNames = names[id].orEmpty().distinct()
            )
        }
        return MusicLibraryState(items, loading = false)
    }

    /** Read-only inspection: a same-size unrelated cache file is never a download match. */
    private fun resolveLibraryFile(song: DownloadedSong): File? {
        val original = File(song.localPath)
        val candidates = listOfNotNull(original, File(File(context.filesDir, "Music"), original.name),
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.let { File(it, original.name) })
        return candidates.firstOrNull { it.isFile && it.length() > 0 }
    }

    fun refresh() { refreshVersion.value += 1 }
    fun clearError() { _error.value = null }

    fun setFavorite(track: MusicTrack, favorite: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            music.setFavorite(uid, track, favorite).onFailure {
                _error.value = "No se pudo guardar el favorito: ${it.message.orEmpty()}"
            }
        }
    }

    fun download(track: MusicTrack) {
        val existing = downloadStates.value[track.id]
        if (existing is MediaDownloadState.Downloading || existing is MediaDownloadState.Queued) return
        if (auth.currentUser == null) return
        mediaDownloads.downloadTracks(listOf(track))
    }
}
