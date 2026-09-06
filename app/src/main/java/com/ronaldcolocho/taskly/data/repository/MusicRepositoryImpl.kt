package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.repository.IMusicRepository
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val firestore: FirebaseFirestore,
    private val mediaCache: IMediaCacheRepository
) : IMusicRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val history = MutableStateFlow(loadHistory())
    private val historyMutex = Mutex()
    private val legacyMigrationMutex = Mutex()
    private val migratedLegacyPlaylists = mutableSetOf<String>()

    override fun observeMostPlayed(): Flow<List<MusicTrack>> = history.map { tracks ->
        tracks.sortedWith(compareByDescending<MusicTrack> { it.playCount }.thenByDescending { it.lastPlayedAt })
            .take(MAX_HISTORY_TRACKS)
    }

    override suspend fun recordPlayback(track: MusicTrack) {
        if (track.id.isBlank() || track.url.isBlank()) return
        withContext(Dispatchers.IO) {
            historyMutex.withLock {
                val now = System.currentTimeMillis()
                val updated = history.value.toMutableList()
                val index = updated.indexOfFirst { it.id == track.id }
                if (index >= 0) {
                    val existing = updated[index]
                    // Resuming the same audio repeatedly is not a new listen.
                    if (now - existing.lastPlayedAt < PLAY_DEDUPLICATION_MS) return@withLock
                    updated[index] = track.copy(
                        playCount = existing.playCount + 1,
                        lastPlayedAt = now,
                        addedAt = existing.addedAt
                    )
                } else {
                    updated += track.copy(playCount = 1, lastPlayedAt = now, addedAt = now)
                }
                val bounded = updated.sortedByDescending { it.lastPlayedAt }.take(MAX_HISTORY_TRACKS)
                persistHistory(bounded)
                history.value = bounded
            }
        }
    }

    override fun observePlaylists(userId: String): Flow<List<MusicPlaylist>> = callbackFlow {
        val listener = playlists(userId).orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().map { document ->
                        MusicPlaylist(
                            id = document.id,
                            userId = document.getString("userId").orEmpty(),
                            name = document.getString("name").orEmpty(),
                            createdAt = document.getLong("createdAt") ?: 0L,
                            updatedAt = document.getLong("updatedAt") ?: 0L,
                            coverUrl = document.getString("coverUrl").orEmpty(),
                            songCount = (document.getLong("songCount") ?: 0L).toInt()
                        )
                    })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<MusicTrack>> = callbackFlow {
        val listener = playlists(userId).document(playlistId).collection("tracks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Unable to read users/$userId/playlists/$playlistId/tracks", error)
                    close(error)
                } else {
                    val tracks = snapshot?.documents.orEmpty()
                        .map { document -> document.toMusicTrack() }
                        .sortedWith(compareBy<MusicTrack> { it.position }.thenByDescending { it.addedAt })
                    Log.d(TAG, "Loaded ${tracks.size} tracks for playlist=$playlistId")
                    trySend(tracks)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun downloadedAudioTracks(): List<MusicTrack> = mediaCache.downloadedAudioReferences().map {
        MusicTrack(
            id = it.mediaId, url = it.url, name = it.name,
            sourceMessageId = it.sourceMessageId, durationSeconds = it.durationSeconds,
            addedAt = it.downloadedAt
        )
    }

    override suspend fun createPlaylist(userId: String, name: String): Result<String> = runCatching {
        require(name.isNotBlank()) { "Escribe un nombre para la lista." }
        val now = System.currentTimeMillis()
        val document = playlists(userId).document()
        document.set(
            mapOf(
                "userId" to userId,
                "name" to name.trim(),
                "createdAt" to now,
                "updatedAt" to now,
                "coverUrl" to "",
                "songCount" to 0
            )
        ).await()
        document.id
    }

    override suspend fun renamePlaylist(userId: String, playlistId: String, name: String): Result<Unit> = runCatching {
        require(name.isNotBlank()) { "Escribe un nombre para la lista." }
        playlists(userId).document(playlistId).update("name", name.trim(), "updatedAt", System.currentTimeMillis()).await()
    }

    override suspend fun deletePlaylist(userId: String, playlistId: String): Result<Unit> = runCatching {
        val playlist = playlists(userId).document(playlistId)
        // Firestore no borra subcolecciones: se eliminan en lotes acotados antes del padre.
        var snapshot = playlist.collection("tracks").limit(MAX_BATCH_WRITES.toLong()).get().await()
        while (!snapshot.isEmpty) {
            val batch = firestore.batch()
            snapshot.documents.forEach { document -> batch.delete(document.reference) }
            batch.commit().await()
            snapshot = playlist.collection("tracks").limit(MAX_BATCH_WRITES.toLong()).get().await()
        }
        playlist.delete().await()
    }

    override suspend fun addTrackToPlaylist(userId: String, playlistId: String, track: MusicTrack): Result<Unit> {
        val trackDocumentId = trackDocumentId(track.id)
        val targetPath = "users/$userId/playlists/$playlistId/tracks/$trackDocumentId"
        Log.d(TAG, "Playlist track write started: $targetPath")
        return runCatching {
            require(userId.isNotBlank() && playlistId.isNotBlank()) { "Lista no valida." }
            require(track.id.isNotBlank() && track.url.isNotBlank()) { "Audio no valido." }
            val playlist = playlists(userId).document(playlistId)
            val trackDocument = playlist.collection("tracks").document(trackDocumentId)
            val now = System.currentTimeMillis()

            // The track document id is the attachment publicId. A transaction keeps this
            // deduplication and the parent count correct even when two taps race.
            firestore.runTransaction { transaction ->
                val alreadyAdded = transaction.get(trackDocument).exists()
                transaction.set(trackDocument, track.toMap(now, if (alreadyAdded) track.position else now))
                transaction.update(
                    playlist,
                    mapOf(
                        "updatedAt" to now,
                        "songCount" to if (alreadyAdded) FieldValue.increment(0) else FieldValue.increment(1)
                    )
                )
            }.await()
            Unit
        }.onSuccess {
            Log.d(TAG, "Playlist track write succeeded: $targetPath")
        }.onFailure { error ->
            Log.e(TAG, "Playlist track write failed: $targetPath", error)
        }
    }

    override suspend fun removeTrackFromPlaylist(userId: String, playlistId: String, trackId: String): Result<Unit> = runCatching {
        val playlist = playlists(userId).document(playlistId)
        val track = playlist.collection("tracks").document(trackDocumentId(trackId))
        firestore.runTransaction { transaction ->
            if (transaction.get(track).exists()) {
                transaction.delete(track)
                transaction.update(playlist, "songCount", FieldValue.increment(-1), "updatedAt", System.currentTimeMillis())
            }
        }.await()
    }

    override suspend fun reorderTracks(userId: String, playlistId: String, orderedTrackIds: List<String>): Result<Unit> = runCatching {
        orderedTrackIds.distinct().chunked(MAX_BATCH_WRITES).forEachIndexed { batchIndex, chunk ->
            val batch = firestore.batch()
            chunk.forEachIndexed { index, id ->
                batch.update(playlists(userId).document(playlistId).collection("tracks").document(trackDocumentId(id)), "position", (batchIndex * MAX_BATCH_WRITES + index).toLong(), "updatedAt", System.currentTimeMillis())
            }
            batch.commit().await()
        }
        playlists(userId).document(playlistId).update("updatedAt", System.currentTimeMillis()).await()
    }

    override suspend fun syncPlaylistSongCount(userId: String, playlistId: String, actualCount: Int) {
        val playlist = playlists(userId).document(playlistId)
        val migratedTracks = migrateLegacyTracks(userId, playlistId)
        val resolvedCount = if (migratedTracks > 0) {
            playlist.collection("tracks").get().await().size()
        } else {
            actualCount
        }
        val snapshot = playlist.get().await()
        val storedCount = (snapshot.getLong("songCount") ?: 0L).toInt()
        if (storedCount != resolvedCount) {
            playlist.update(mapOf("songCount" to resolvedCount, "updatedAt" to System.currentTimeMillis())).await()
            Log.w(TAG, "Reconciled songCount=$storedCount to actualCount=$resolvedCount for playlist=$playlistId")
        }
    }

    private fun playlists(userId: String) = firestore.collection("users").document(userId).collection("playlists")

    private fun com.google.firebase.firestore.DocumentSnapshot.toMusicTrack() = MusicTrack(
        id = getString("mediaId").orEmpty().ifBlank { id },
        url = getString("url").orEmpty(),
        name = getString("name").orEmpty(),
        sourceMessageId = getString("sourceMessageId").orEmpty(),
        durationSeconds = (getLong("durationSeconds") ?: 0L).toInt(),
        thumbnailUrl = getString("thumbnailUrl").orEmpty(),
        addedAt = getLong("addedAt") ?: 0L,
        position = getLong("position") ?: (getLong("addedAt") ?: 0L)
    )

    private fun MusicTrack.toMap(addedAt: Long, position: Long) = mapOf(
        "mediaId" to id,
        "url" to url,
        "name" to name,
        "sourceMessageId" to sourceMessageId,
        "durationSeconds" to durationSeconds,
        "thumbnailUrl" to thumbnailUrl,
        "addedAt" to addedAt
        , "position" to position
    )

    /**
     * Cloudinary public ids contain '/'. Firestore interprets those as path separators,
     * so they cannot be used directly as document ids.
     */
    private fun trackDocumentId(mediaId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(mediaId.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    /** Copies, but never deletes, tracks written by older builds under tracks/taskly/{uid}. */
    private suspend fun migrateLegacyTracks(userId: String, playlistId: String): Int = legacyMigrationMutex.withLock {
        val migrationKey = "$userId/$playlistId"
        if (!migratedLegacyPlaylists.add(migrationKey)) return@withLock 0

        try {
            val tracks = playlists(userId).document(playlistId).collection("tracks")
            val legacyDocuments = tracks.document(LEGACY_TRACK_ROOT).collection(userId).get().await().documents
            if (legacyDocuments.isEmpty()) return@withLock 0

            var migrated = 0
            legacyDocuments.chunked(MAX_BATCH_WRITES).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { document ->
                    val mediaId = document.getString("mediaId").orEmpty()
                        .ifBlank { "$LEGACY_TRACK_ROOT/$userId/${document.id}" }
                    val data = document.data.orEmpty().toMutableMap().apply { put("mediaId", mediaId) }
                    batch.set(tracks.document(trackDocumentId(mediaId)), data)
                    migrated++
                }
                batch.commit().await()
            }
            Log.w(TAG, "Migrated $migrated legacy tracks for playlist=$playlistId without deleting originals")
            migrated
        } catch (error: Throwable) {
            migratedLegacyPlaylists.remove(migrationKey)
            Log.e(TAG, "Unable to migrate legacy tracks for playlist=$playlistId", error)
            throw error
        }
    }

    private fun loadHistory(): List<MusicTrack> = runCatching {
        val array = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    MusicTrack(
                        id = item.optString("id"),
                        url = item.optString("url"),
                        name = item.optString("name"),
                        sourceMessageId = item.optString("sourceMessageId"),
                        durationSeconds = item.optInt("durationSeconds"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        playCount = item.optInt("playCount"),
                        lastPlayedAt = item.optLong("lastPlayedAt"),
                        addedAt = item.optLong("addedAt")
                    )
                )
            }
        }.filter { it.id.isNotBlank() && it.url.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun persistHistory(tracks: List<MusicTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("id", track.id)
                put("url", track.url)
                put("name", track.name)
                put("sourceMessageId", track.sourceMessageId)
                put("durationSeconds", track.durationSeconds)
                put("thumbnailUrl", track.thumbnailUrl)
                put("playCount", track.playCount)
                put("lastPlayedAt", track.lastPlayedAt)
                put("addedAt", track.addedAt)
            })
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val TAG = "MusicRepository"
        const val PREFERENCES_NAME = "music_history"
        const val KEY_HISTORY = "tracks"
        const val MAX_HISTORY_TRACKS = 100
        const val PLAY_DEDUPLICATION_MS = 30_000L
        const val LEGACY_TRACK_ROOT = "taskly"
        const val MAX_BATCH_WRITES = 400
    }
}
