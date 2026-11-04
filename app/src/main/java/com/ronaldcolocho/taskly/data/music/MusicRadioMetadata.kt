package com.ronaldcolocho.taskly.data.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ronaldcolocho.taskly.domain.model.MusicTrack
import com.ronaldcolocho.taskly.domain.model.OnlineMusicSource
import java.io.IOException

/** Only artist navigation entries from the music catalog identify a musical artist. */
internal fun parseMusicRadioMetadata(json: String, seedId: String): List<MusicTrack> {
    val root = JsonParser.parseString(json).asJsonObject
    val tabs = root.obj("contents")?.obj("singleColumnMusicWatchNextResultsRenderer")
        ?.obj("tabbedRenderer")?.obj("watchNextTabbedResultsRenderer")?.getAsJsonArray("tabs")
        ?: throw IOException("Respuesta de radio musical no válida")
    val panel = tabs.firstNotNullOfOrNull { tab -> tab.asJsonObject.obj("tabRenderer")?.obj("content")
        ?.obj("musicQueueRenderer")?.obj("content")?.obj("playlistPanelRenderer") }
        ?: throw IOException("Cola musical no disponible")
    return panel.getAsJsonArray("contents")?.toList().orEmpty().take(100).mapNotNull { item ->
        val video = item.asJsonObject.obj("playlistPanelVideoRenderer") ?: return@mapNotNull null
        val id = video.get("videoId")?.asString.orEmpty()
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}")) || id == seedId) return@mapNotNull null
        val name = video.obj("title")?.text().orEmpty()
        val duration = video.obj("lengthText")?.text()?.split(':')?.takeIf { it.size in 2..3 }
            ?.fold(0L) { seconds, part -> seconds * 60 + (part.toLongOrNull() ?: return@mapNotNull null) }
            ?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt() ?: return@mapNotNull null
        if (name.isBlank()) return@mapNotNull null
        val artists = video.obj("longBylineText")?.getAsJsonArray("runs")?.toList().orEmpty().mapNotNull { run ->
            val value = run.asJsonObject
            val browse = value.obj("navigationEndpoint")?.obj("browseEndpoint")
            val type = browse?.obj("browseEndpointContextSupportedConfigs")?.obj("browseEndpointContextMusicConfig")
                ?.get("pageType")?.asString
            value.get("text")?.asString?.takeIf { type == "MUSIC_PAGE_TYPE_ARTIST" && !browse?.get("browseId")?.asString.isNullOrBlank() }
        }.filter { it.isNotBlank() }.distinct()
        val thumbnail = video.obj("thumbnail")?.getAsJsonArray("thumbnails")?.toList().orEmpty()
            .map { it.asJsonObject }.maxByOrNull { it.get("height")?.asInt ?: 0 }?.get("url")?.asString.orEmpty()
        val channel = if (artists.isEmpty()) video.obj("shortBylineText")?.text().orEmpty() else ""
        MusicTrack(OnlineMusicSource.trackId(id), OnlineMusicSource.reference(id), name,
            durationSeconds = duration, thumbnailUrl = thumbnail, artist = artists.joinToString("、"), sourceChannel = channel)
    }.distinctBy { it.id }.take(10)
}

private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.text(): String = get("simpleText")?.asString
    ?: getAsJsonArray("runs")?.toList().orEmpty().joinToString("") { it.asJsonObject.get("text")?.asString.orEmpty() }
