package com.ronaldcolocho.taskly.media

import android.content.Context
import com.google.gson.Gson
import com.ronaldcolocho.taskly.domain.model.CachedAudioReference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Only pending music is journaled; completed files remain in the existing audio catalog. */
@Singleton
class MusicDownloadStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("pending_music_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun pending(): List<CachedAudioReference> = preferences.all.values.mapNotNull { value ->
        runCatching { gson.fromJson(value as String, CachedAudioReference::class.java)
            .takeIf { it.mediaId.isNotBlank() && it.url.isNotBlank() } }.getOrNull()
    }
    fun put(reference: CachedAudioReference) {
        check(preferences.edit().putString(reference.mediaId, gson.toJson(reference)).commit()) {
            "No se pudo guardar la cola de descargas."
        }
    }
    fun remove(id: String) { preferences.edit().remove(id).apply() }
}
