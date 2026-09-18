package com.ronaldcolocho.taskly.domain.model

data class DownloadedSong(
    val id: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Int = 0,
    val localPath: String,
    val mimeType: String = "audio/mpeg",
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isSavedToMessages: Boolean = false,
    val savedMessageId: String? = null
)
