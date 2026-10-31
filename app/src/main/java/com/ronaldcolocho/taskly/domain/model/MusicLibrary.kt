package com.ronaldcolocho.taskly.domain.model

import java.text.Normalizer

data class MusicLibraryItem(
    val track: MusicTrack,
    val favorite: Boolean = false,
    val localPath: String? = null,
    val sizeBytes: Long = 0,
    val wasDownloaded: Boolean = false,
    val playlistNames: List<String> = emptyList()
) {
    val availableOffline: Boolean get() = localPath != null
    val needsDownload: Boolean get() = wasDownloaded && !availableOffline
    val canDownload: Boolean get() = track.url.startsWith("https://") || track.url.startsWith("http://")
}

enum class MusicLibraryFilter(val label: String) {
    ALL("Todas"), FAVORITES("Favoritas"), OFFLINE("Descargadas"), MISSING("Por recuperar")
}

enum class MusicLibraryOrder(val label: String) {
    RECENT("Recientes"), NAME("A–Z"), MOST_PLAYED("Más escuchadas"), SIZE("Mayor tamaño")
}

fun filterMusicLibrary(
    items: List<MusicLibraryItem>, query: String, filter: MusicLibraryFilter, order: MusicLibraryOrder
): List<MusicLibraryItem> {
    val terms = normalizeMusicQuery(query).split(' ').filter { it.isNotBlank() }
    return items.filter { item ->
        val matchesFilter = when (filter) {
            MusicLibraryFilter.ALL -> true
            MusicLibraryFilter.FAVORITES -> item.favorite
            MusicLibraryFilter.OFFLINE -> item.availableOffline
            MusicLibraryFilter.MISSING -> item.needsDownload
        }
        val text = normalizeMusicQuery(item.track.name + " " + item.track.artist + " " + item.playlistNames.joinToString(" "))
        matchesFilter && terms.all { it in text }
    }.sortedWith(when (order) {
        MusicLibraryOrder.RECENT -> compareByDescending<MusicLibraryItem> { it.track.addedAt }
        MusicLibraryOrder.NAME -> compareBy { normalizeMusicQuery(it.track.name) }
        MusicLibraryOrder.MOST_PLAYED -> compareByDescending<MusicLibraryItem> { it.track.playCount }
            .thenByDescending { it.track.lastPlayedAt }
        MusicLibraryOrder.SIZE -> compareByDescending { it.sizeBytes }
    })
}

private fun normalizeMusicQuery(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).trim()
