package com.ronaldcolocho.taskly.ui.screen.music

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.ronaldcolocho.taskly.data.music.OnlineMusicRepository
import com.ronaldcolocho.taskly.domain.model.CachedAudioReference
import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import java.io.IOException
import javax.inject.Inject

enum class MusicSearchCategory(val label: String) {
    ALL("Todo"),
    SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes")
}

data class MusicDiscoverState(
    val query: String = "",
    val searchedQuery: String = "",
    val tracks: List<MusicTrack> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
    val savingIds: Set<String> = emptySet(),
    val downloadedIds: Set<String> = emptySet(),
    val recentSearches: List<String> = emptyList(),
    val selectedCategory: MusicSearchCategory = MusicSearchCategory.ALL
)

@HiltViewModel
class MusicDiscoverViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val online: OnlineMusicRepository,
    private val music: IMusicRepository,
    private val auth: FirebaseAuth,
    private val downloads: MediaDownloadManager,
    private val cache: IMediaCacheRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val _state = MutableStateFlow(
        MusicDiscoverState(
            query = savedState["music_discover_query"] ?: "",
            recentSearches = loadRecentSearches()
        )
    )
    val state = _state.asStateFlow()
    val downloadStates = downloads.states
    private val uid = auth.currentUser?.uid
    val favorites = uid?.let { music.observeFavorites(it).map { tracks -> tracks.map { track -> track.id }.toSet() }
        .catch { _state.update { it.copy(feedback = "No se pudieron cargar las favoritas.") }; emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()) } ?: MutableStateFlow(emptySet())
    val playlists = uid?.let { music.observePlaylists(it).catch {
        _state.update { it.copy(feedback = "No se pudieron cargar las listas.") }; emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) } ?: MutableStateFlow(emptyList())
    private var searchJob: Job? = null
    private var generation = 0
    private var nextPage: Page? = null

    init {
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { cache.downloadedAudioReferences() }
            _state.update { it.copy(downloadedIds = it.downloadedIds + local.map { track -> track.mediaId }) }
        }
        viewModelScope.launch {
            downloadStates.collect { states ->
                val completed = states.filterValues { it is MediaDownloadState.Downloaded }.keys
                if (completed.isNotEmpty()) _state.update { it.copy(downloadedIds = it.downloadedIds + completed) }
            }
        }
    }

    private fun loadRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null)
        return if (raw.isNullOrBlank()) {
            listOf("cumbia", "baladas románticas", "música para estudiar")
        } else {
            raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    private fun persistRecentSearches(list: List<String>) {
        prefs.edit().putString(KEY_RECENT_SEARCHES, list.joinToString("\n")).apply()
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _state.value.recentSearches
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
        persistRecentSearches(updated)
        _state.update { it.copy(recentSearches = updated) }
    }

    fun removeRecentSearch(query: String) {
        val updated = _state.value.recentSearches.filterNot { it.equals(query.trim(), ignoreCase = true) }
        persistRecentSearches(updated)
        _state.update { it.copy(recentSearches = updated) }
    }

    fun clearRecentSearches() {
        persistRecentSearches(emptyList())
        _state.update { it.copy(recentSearches = emptyList()) }
    }

    fun selectCategory(category: MusicSearchCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun queryChanged(query: String) {
        savedState["music_discover_query"] = query
        generation++
        searchJob?.cancel()
        _state.update { it.copy(query = query, loading = false, error = null) }
    }

    fun clearSearch() {
        savedState["music_discover_query"] = ""
        generation++
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                searchedQuery = "",
                tracks = emptyList(),
                loading = false,
                error = null,
                selectedCategory = MusicSearchCategory.ALL
            )
        }
    }

    fun searchDirect(query: String) {
        queryChanged(query)
        search()
    }

    fun search(more: Boolean = false) {
        val query = state.value.query.trim()
        if (query.isBlank()) return
        if (!more) {
            addRecentSearch(query)
        }
        val append = more && query == state.value.searchedQuery
        val page = if (append) nextPage ?: return else null
        searchJob?.cancel()
        val request = ++generation
        _state.update { it.copy(loading = true, error = null, searchedQuery = query,
            tracks = if (append) it.tracks else emptyList(), hasMore = if (append) it.hasMore else false) }
        searchJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { online.search(query, page) }
                if (request != generation) return@launch
                nextPage = result.nextPage
                _state.update { it.copy(tracks = (if (append) it.tracks + result.tracks else result.tracks).distinctBy(MusicTrack::id),
                    loading = false, hasMore = result.nextPage != null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == generation) _state.update { it.copy(loading = false,
                    error = if (error is IOException) "No se pudo conectar. Revisa tu Internet y vuelve a buscar."
                    else "YouTube Music no pudo completar la búsqueda. Vuelve a intentarlo más tarde.") }
            }
        }
    }

    fun favorite(track: MusicTrack, favorite: Boolean) = save(track) { user ->
        music.setFavorite(user, track, favorite).getOrThrow()
        if (favorite) "Guardada en favoritas" else "Quitada de favoritas"
    }

    fun addToPlaylist(track: MusicTrack, playlistId: String) = save(track) { user ->
        music.addTrackToPlaylist(user, playlistId, track).getOrThrow()
        "Canción guardada en la lista"
    }

    fun createPlaylist(track: MusicTrack, name: String) = save(track) { user ->
        require(name.isNotBlank()) { "Escribe un nombre para la lista." }
        val playlist = music.createPlaylist(user, name.trim()).getOrThrow()
        music.addTrackToPlaylist(user, playlist, track).getOrThrow()
        "Lista creada y canción guardada"
    }

    private fun save(track: MusicTrack, action: suspend (String) -> String) {
        val user = auth.currentUser?.uid ?: run { feedback("Inicia sesión para guardar canciones."); return }
        if (track.id in state.value.savingIds) return
        _state.update { it.copy(savingIds = it.savingIds + track.id) }
        viewModelScope.launch {
            try { feedback(action(user)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { feedback("No se pudo guardar la canción. Vuelve a intentarlo.") }
            finally { _state.update { it.copy(savingIds = it.savingIds - track.id) } }
        }
    }

    fun download(track: MusicTrack) {
        if (auth.currentUser == null) { feedback("Inicia sesión para descargar canciones."); return }
        if (track.id in state.value.downloadedIds || downloadStates.value[track.id] is MediaDownloadState.Downloading ||
            downloadStates.value[track.id] is MediaDownloadState.Queued) return
        downloads.downloadAudio(CachedAudioReference(track.id, track.url, track.name, track.sourceMessageId,
            track.durationSeconds, System.currentTimeMillis(), track.artist, track.thumbnailUrl, track.sourceChannel))
        feedback("Descarga agregada. Podrás encontrarla en Mi biblioteca.")
    }

    fun feedback(message: String) { _state.update { it.copy(feedback = message) } }
    fun clearFeedback() { _state.update { it.copy(feedback = null) } }

    private companion object {
        const val PREFS_NAME = "taskly_music_discover"
        const val KEY_RECENT_SEARCHES = "recent_searches"
    }
}
