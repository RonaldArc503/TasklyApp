package com.ronaldcolocho.taskly.domain.model

data class MusicTrack(
    val id: String,
    val url: String,
    val name: String,
    val sourceMessageId: String = "",
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val addedAt: Long = 0L
    , val position: Long = 0L,
    val artist: String = "",
    val sourceChannel: String = ""
)

data class MusicPlaylist(
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverUrl: String = "",
    val songCount: Int = 0,
    val sharedOwnerId: String = "",
    val sharedPlaylistId: String = "",
    val shareId: String = ""
) {
    val editable: Boolean get() = sharedOwnerId.isBlank()
}

data class CachedAudioReference(
    val mediaId: String,
    val url: String,
    val name: String,
    val sourceMessageId: String,
    val durationSeconds: Int,
    val downloadedAt: Long,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val sourceChannel: String = ""
)

fun selectMusicPlaylist(
    owned: List<MusicPlaylist>, shared: MusicPlaylist?, userId: String?, requestedId: String
): MusicPlaylist? = owned.firstOrNull { it.id == requestedId && it.editable }
    ?: owned.firstOrNull { shared != null && shared.sharedOwnerId == userId && it.id == shared.sharedPlaylistId }
    ?: shared ?: owned.firstOrNull { it.id == requestedId }
