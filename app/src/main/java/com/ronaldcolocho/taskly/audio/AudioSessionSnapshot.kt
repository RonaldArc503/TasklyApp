package com.ronaldcolocho.taskly.audio

import com.google.gson.Gson
import com.google.gson.JsonParser

/** A paused resume point. Playback intent is deliberately never persisted. */
data class AudioSessionSnapshot(
    val tracks: List<AudioTrack>,
    val currentId: String?,
    val positionMs: Long,
    val mode: AudioMode,
    val repeatMode: AudioRepeatMode,
    val shuffle: Boolean
) {
    fun toState() = AudioPlayerState(
        tracks = tracks, currentTrack = tracks.firstOrNull { it.id == currentId },
        currentTimeMs = positionMs.coerceAtLeast(0), mode = mode, repeatMode = repeatMode,
        shuffleModeEnabled = shuffle,
        durationMs = (tracks.firstOrNull { it.id == currentId }?.durationSeconds ?: 0) * 1000L
    )

    companion object {
        fun from(state: AudioPlayerState) = AudioSessionSnapshot(state.tracks, state.currentTrack?.id,
            state.currentTimeMs.coerceAtLeast(0), state.mode, state.repeatMode, state.shuffleModeEnabled)
    }
}

object AudioSessionCodec {
    private val gson = Gson()
    fun encode(snapshot: AudioSessionSnapshot): String = gson.toJson(snapshot)

    fun decode(json: String?): AudioSessionSnapshot? = runCatching {
        if (json.isNullOrBlank()) return null
        val root = JsonParser.parseString(json).asJsonObject
        val tracks = root.getAsJsonArray("tracks").mapNotNull { value ->
            runCatching {
                val item = value.asJsonObject
                fun string(key: String) = item.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                AudioTrack(string("id"), string("url"), string("name"), string("msgId"),
                    item.get("durationSeconds")?.asInt?.coerceAtLeast(0) ?: 0,
                    string("thumbnailUrl"), string("playlistId").ifBlank { null }, string("artist"),
                    item.get("relatedAutoplay")?.asBoolean ?: false, string("sourceChannel"))
                    .takeIf { it.id.isNotBlank() && it.url.isNotBlank() }
            }.getOrNull()
        }.distinctBy { it.id }
        val currentId = root.get("currentId")?.takeUnless { it.isJsonNull }?.asString
        if (tracks.none { it.id == currentId }) return null
        AudioSessionSnapshot(tracks, currentId, root.get("positionMs")?.asLong?.coerceAtLeast(0) ?: 0,
            runCatching { AudioMode.valueOf(root.get("mode").asString) }.getOrDefault(AudioMode.QUEUE),
            runCatching { AudioRepeatMode.valueOf(root.get("repeatMode").asString) }.getOrDefault(AudioRepeatMode.OFF),
            root.get("shuffle")?.asBoolean ?: false)
    }.getOrNull()
}

data class AudioQueueEdit(val tracks: List<AudioTrack>, val currentIndex: Int, val positionMs: Long)

fun planAudioQueueEdit(
    previous: List<AudioTrack>, currentId: String?, positionMs: Long, requested: List<AudioTrack>
): AudioQueueEdit {
    val tracks = requested.distinctBy { it.id }
    if (tracks.isEmpty()) return AudioQueueEdit(tracks, -1, 0)
    val retained = tracks.indexOfFirst { it.id == currentId }
    if (retained >= 0) return AudioQueueEdit(tracks, retained, positionMs.coerceAtLeast(0))
    val oldIndex = previous.indexOfFirst { it.id == currentId }
    val next = previous.drop((oldIndex + 1).coerceAtLeast(0)).firstOrNull { old -> tracks.any { it.id == old.id } }
    return AudioQueueEdit(tracks, next?.let { song -> tracks.indexOfFirst { it.id == song.id } }
        ?: oldIndex.coerceIn(0, tracks.lastIndex), 0)
}
