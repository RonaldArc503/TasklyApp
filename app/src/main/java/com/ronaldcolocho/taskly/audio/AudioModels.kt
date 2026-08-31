package com.ronaldcolocho.taskly.audio

data class AudioTrack(
    val id: String,
    val url: String,
    val name: String,
    val msgId: String
)

enum class AudioMode { INDIVIDUAL, QUEUE, REPEAT, LOOP }

data class AudioPlayerState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val playing: Boolean = false,
    val mode: AudioMode = AudioMode.INDIVIDUAL,
    val currentTimeMs: Long = 0,
    val durationMs: Long = 0
)
