package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import com.ronaldcolocho.taskly.domain.model.MusicPlaylistLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SharedMusicPlaylistRepository @Inject constructor(private val firestore: FirebaseFirestore) {
    private fun playlist(uid: String, id: String) =
        firestore.collection("users").document(uid).collection("playlists").document(id)

    suspend fun share(uid: String, list: MusicPlaylist): String {
        if (!list.editable) return MusicPlaylistLink.create(list.shareId)
        require(list.userId == uid) { "No puedes compartir esta lista." }
        val reference = playlist(uid, list.id)
        val candidate = UUID.randomUUID().toString().replace("-", "")
        val id = firestore.runTransaction { tx ->
            val document = tx.get(reference)
            check(document.exists()) { "La lista ya no está disponible." }
            val existing = document.getString("shareId").orEmpty()
            if (existing.isNotBlank()) existing else {
                tx.set(firestore.collection("sharedMusicPlaylists").document(candidate),
                    mapOf("ownerId" to uid, "playlistId" to list.id))
                tx.update(reference, "shareId", candidate)
                candidate
            }
        }.await()
        return MusicPlaylistLink.create(id)
    }

    fun observe(shareId: String): Flow<MusicPlaylist?> {
        MusicPlaylistLink.create(shareId)
        return callbackFlow {
            val listener = firestore.collection("sharedMusicPlaylists").document(shareId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) close(error) else trySend(snapshot?.data)
                }
            awaitClose { listener.remove() }
        }.flatMapLatest { data ->
            val uid = data?.get("ownerId") as? String
            val id = data?.get("playlistId") as? String
            if (uid.isNullOrBlank() || id.isNullOrBlank()) flowOf(null)
            else callbackFlow {
                val listener = playlist(uid, id).addSnapshotListener { snapshot, error ->
                    if (error != null) close(error)
                    else trySend(snapshot?.takeIf { it.exists() }?.toMusicPlaylist()?.copy(
                        sharedOwnerId = uid, sharedPlaylistId = id, shareId = shareId))
                }
                awaitClose { listener.remove() }
            }
        }
    }

    /** Copies into one owned editable list, including migration of old saved references. */
    suspend fun save(uid: String, list: MusicPlaylist): String {
        require(uid.isNotBlank() && !list.editable && list.sharedPlaylistId.isNotBlank() && list.shareId.isNotBlank())
        if (list.sharedOwnerId == uid) return list.sharedPlaylistId
        val id = MusicPlaylistLink.savedPlaylistId(list.shareId)
        val target = playlist(uid, id)
        copySharedPlaylist(firestore, target, playlist(list.sharedOwnerId, list.sharedPlaylistId), uid, list.shareId)
        return id
    }
}
