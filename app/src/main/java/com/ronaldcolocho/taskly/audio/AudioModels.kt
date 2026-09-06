package com.ronaldcolocho.taskly.audio

data class AudioTrack(
    val id: String,
    val url: String,
    val name: String,
    val msgId: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = ""
)

enum class AudioMode { INDIVIDUAL, QUEUE, REPEAT, LOOP }

enum class AudioRepeatMode {
    OFF, ALL, ONE;

    fun next(): AudioRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

data class AudioPlayerState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val playing: Boolean = false,
    val mode: AudioMode = AudioMode.INDIVIDUAL,
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val sleepTimerEndAt: Long? = null,
    val currentTimeMs: Long = 0,
    val durationMs: Long = 0
    , val error: String? = null
)
