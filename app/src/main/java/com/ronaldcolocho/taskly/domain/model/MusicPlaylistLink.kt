package com.ronaldcolocho.taskly.domain.model

import java.net.URI

/** A random capability identifies one shared list; private user paths never appear in links. */
object MusicPlaylistLink {
    private val token = Regex("[a-f0-9]{32}")
    fun create(shareId: String): String {
        require(token.matches(shareId)) { "Enlace de lista no válido." }
        return "https://tasksv.web.app/musica/lista/$shareId"
    }
    fun parse(value: String?): String? = runCatching {
        val uri = URI(value ?: return null)
        if (uri.rawFragment != null || uri.userInfo != null || uri.port != -1) return null
        when {
            uri.scheme == "taskly" && uri.host == "playlist" && uri.rawQuery == null ->
                uri.path.removePrefix("/").takeIf(token::matches)
            uri.scheme == "https" && uri.host in setOf("tasksv.web.app", "tasksv.firebaseapp.com") ->
                Regex("^/musica/lista/([a-f0-9]{32})/?$").matchEntire(uri.path)?.groupValues?.get(1)
            else -> null
        }
    }.getOrNull()
    fun savedPlaylistId(shareId: String): String {
        require(token.matches(shareId))
        return "shared_$shareId"
    }
}
