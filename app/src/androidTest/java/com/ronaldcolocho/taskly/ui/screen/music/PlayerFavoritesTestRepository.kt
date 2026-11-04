package com.ronaldcolocho.taskly.ui.screen.music

import com.ronaldcolocho.taskly.domain.model.*
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** In-memory backend: these UI checks never write to production Firestore. */
internal class PlayerFavoritesTestRepository : IMusicRepository {
    val liked = MutableStateFlow<List<MusicTrack>>(emptyList())
    var writes = 0
    var failNext = false
    override fun observeFavorites(userId: String): Flow<List<MusicTrack>> = liked
    override suspend fun setFavorite(userId: String, track: MusicTrack, favorite: Boolean): Result<Unit> {
        writes++
        delay(100)
        if (failNext) { failNext = false; return Result.failure(java.io.IOException("Fixture failure")) }
        liked.value = liked.value.filterNot { it.id == track.id } + if (favorite) listOf(track) else emptyList()
        return Result.success(Unit)
    }
    override fun observeMostPlayed(): Flow<List<MusicTrack>> = flowOf(emptyList())
    override suspend fun recordPlayback(track: MusicTrack) = Unit
    override fun observePlaylists(userId: String): Flow<List<MusicPlaylist>> = flowOf(emptyList())
    override fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<MusicTrack>> = flowOf(emptyList())
    override suspend fun downloadedAudioTracks(): List<MusicTrack> = emptyList()
    override suspend fun createPlaylist(userId: String, name: String): Result<String> = error("Unexpected playlist write")
    override suspend fun renamePlaylist(userId: String, playlistId: String, name: String): Result<Unit> = error("Unexpected playlist write")
    override suspend fun deletePlaylist(userId: String, playlistId: String): Result<Unit> = error("Unexpected playlist write")
    override suspend fun addTrackToPlaylist(userId: String, playlistId: String, track: MusicTrack): Result<Unit> = error("Unexpected playlist write")
    override suspend fun removeTrackFromPlaylist(userId: String, playlistId: String, trackId: String): Result<Unit> = error("Unexpected playlist write")
    override suspend fun reorderTracks(userId: String, playlistId: String, orderedTrackIds: List<String>): Result<Unit> = error("Unexpected playlist write")
    override suspend fun syncPlaylistSongCount(userId: String, playlistId: String, actualCount: Int) = Unit
}
