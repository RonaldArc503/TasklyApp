package com.ronaldcolocho.taskly.audio

data class AudioTrack(
    val id: String,
    val url: String,
    val name: String,
    val msgId: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val playlistId: String? = null,
    val artist: String = "",
    val relatedAutoplay: Boolean = false,
    val sourceChannel: String = ""
)

/** Browsing starts a radio from one song; a saved playlist keeps its complete explicit queue. */
fun playbackQueueForSelection(tracks: List<AudioTrack>, selected: AudioTrack): List<AudioTrack> =
    if (selected.playlistId == null && com.ronaldcolocho.taskly.domain.model.OnlineMusicSource.youtubeId(selected.url) != null)
        listOf(selected.copy(relatedAutoplay = true))
    else tracks.map { it.copy(relatedAutoplay = false) }

enum class AudioMode { INDIVIDUAL, QUEUE, REPEAT, LOOP }

enum class AudioRepeatMode {
    OFF, ALL, ONE;

    fun next(): AudioRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

enum class QueueResult {
    ALREADY_PLAYING,
    PLAYING_NOW,
    ADDED_AS_NEXT,
    ADDED_TO_QUEUE
}

data class AudioPlayerState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val playing: Boolean = false,
    val mode: AudioMode = AudioMode.INDIVIDUAL,
    val repeatMode: AudioRepeatMode = AudioRepeatMode.OFF,
    val shuffleModeEnabled: Boolean = false,
    val sleepTimerEndAt: Long? = null,
    val currentTimeMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val buffering: Boolean = false,
    val radioLoading: Boolean = false,
    val radioError: String? = null
)
