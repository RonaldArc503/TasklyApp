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
    , val position: Long = 0L
)

data class MusicPlaylist(
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverUrl: String = "",
    val songCount: Int = 0
)

data class CachedAudioReference(
    val mediaId: String,
    val url: String,
    val name: String,
    val sourceMessageId: String,
    val durationSeconds: Int,
    val downloadedAt: Long
)
