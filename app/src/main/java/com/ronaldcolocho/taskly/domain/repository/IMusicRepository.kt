package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import kotlinx.coroutines.flow.Flow

interface IMusicRepository {
    fun observeMostPlayed(): Flow<List<MusicTrack>>
    suspend fun recordPlayback(track: MusicTrack)
    fun observePlaylists(userId: String): Flow<List<MusicPlaylist>>
    fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<MusicTrack>>
    suspend fun downloadedAudioTracks(): List<MusicTrack>
    suspend fun createPlaylist(userId: String, name: String): Result<String>
    suspend fun renamePlaylist(userId: String, playlistId: String, name: String): Result<Unit>
    suspend fun deletePlaylist(userId: String, playlistId: String): Result<Unit>
    suspend fun addTrackToPlaylist(userId: String, playlistId: String, track: MusicTrack): Result<Unit>
    suspend fun removeTrackFromPlaylist(userId: String, playlistId: String, trackId: String): Result<Unit>
    suspend fun reorderTracks(userId: String, playlistId: String, orderedTrackIds: List<String>): Result<Unit>
    suspend fun syncPlaylistSongCount(userId: String, playlistId: String, actualCount: Int)
}
