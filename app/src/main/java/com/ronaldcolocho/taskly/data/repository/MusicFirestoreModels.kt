package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.ronaldcolocho.taskly.domain.model.MusicPlaylist
import java.security.MessageDigest

internal fun trackDocumentId(mediaId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(mediaId.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun DocumentSnapshot.toMusicPlaylist() = MusicPlaylist(
    id = id, userId = getString("userId").orEmpty(), name = getString("name").orEmpty(),
    createdAt = getLong("createdAt") ?: 0, updatedAt = getLong("updatedAt") ?: 0,
    coverUrl = getString("coverUrl").orEmpty(), songCount = (getLong("songCount") ?: 0).toInt(),
    sharedOwnerId = getString("sharedOwnerId").orEmpty(),
    sharedPlaylistId = getString("sharedPlaylistId").orEmpty(), shareId = getString("shareId").orEmpty()
)
