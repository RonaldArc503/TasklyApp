package com.ronaldcolocho.taskly.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

data class PendingDownloadMetadata(
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val targetPath: String,
    val mimeType: String = "audio/mpeg"
)

object DownloadTracker {
    private const val PREFS_NAME = "taskly_pending_downloads"
    private const val KEY_PENDING = "pending_map"
    private val memoryMap = ConcurrentHashMap<Long, PendingDownloadMetadata>()
    private val gson = Gson()

    fun registerPending(context: Context, downloadId: Long, metadata: PendingDownloadMetadata) {
        memoryMap[downloadId] = metadata
        saveToPrefs(context)
    }

    fun getAndRemove(context: Context, downloadId: Long): PendingDownloadMetadata? {
        loadFromPrefsIfNeeded(context)
        val removed = memoryMap.remove(downloadId)
        saveToPrefs(context)
        return removed
    }

    private fun loadFromPrefsIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_PENDING, null) ?: return
            val type = object : TypeToken<Map<Long, PendingDownloadMetadata>>() {}.type
            val saved: Map<Long, PendingDownloadMetadata> = gson.fromJson(json, type) ?: return
            saved.forEach { (k, v) -> memoryMap.putIfAbsent(k, v) }
        } catch (_: Exception) {}
    }

    private fun saveToPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(memoryMap)
            prefs.edit().putString(KEY_PENDING, json).apply()
        } catch (_: Exception) {}
    }
}
