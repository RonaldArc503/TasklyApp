package com.ronaldcolocho.taskly.domain.model

import java.net.URI

/** Persist the public song reference, never a signed CDN address. */
object OnlineMusicSource {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun youtubeId(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https") return null
        val id = when (uri.host?.lowercase(java.util.Locale.ROOT)) {
            "youtu.be" -> uri.path.removePrefix("/")
            "www.youtube.com", "youtube.com", "music.youtube.com" -> {
                if (uri.path != "/watch") return null
                uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            }
            else -> null
        }
        id?.takeIf { videoId.matches(it) }
    }.getOrNull()

    fun reference(id: String): String {
        require(videoId.matches(id)) { "Identificador de canción no válido." }
        return "https://www.youtube.com/watch?v=$id"
    }

    fun trackId(id: String): String {
        reference(id)
        return "youtube:$id"
    }

    fun cacheKey(url: String): String = youtubeId(url)?.let { "youtube-audio:$it" } ?: url
}
