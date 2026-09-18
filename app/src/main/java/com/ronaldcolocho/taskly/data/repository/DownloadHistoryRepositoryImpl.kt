package com.ronaldcolocho.taskly.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadHistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IDownloadHistoryRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _songs = MutableStateFlow<List<DownloadedSong>>(emptyList())

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    override fun getDownloadedSongs(): Flow<List<DownloadedSong>> = _songs.asStateFlow()

    override suspend fun addDownloadedSong(song: DownloadedSong) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _songs.value.toMutableList()
            // Remove existing entry with same id or same local path if any
            current.removeAll { it.id == song.id || it.localPath == song.localPath }
            current.add(0, song)
            saveToDisk(current)
            _songs.value = current
        }
    }

    override suspend fun removeDownloadedSong(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _songs.value.filterNot { it.id == id }
            saveToDisk(current)
            _songs.value = current
        }
    }

    override suspend fun updateSavedToMessages(id: String, isSaved: Boolean, messageId: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _songs.value.map { song ->
                if (song.id == id) {
                    song.copy(isSavedToMessages = isSaved, savedMessageId = messageId)
                } else song
            }
            saveToDisk(current)
            _songs.value = current
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().remove(KEY_SONGS).apply()
            _songs.value = emptyList()
        }
    }

    private fun loadFromDisk() {
        try {
            val json = prefs.getString(KEY_SONGS, null) ?: return
            val type = object : TypeToken<List<DownloadedSong>>() {}.type
            val list: List<DownloadedSong>? = gson.fromJson(json, type)
            if (list != null) {
                _songs.value = list
            }
        } catch (_: Exception) {
            _songs.value = emptyList()
        }
    }

    private fun saveToDisk(list: List<DownloadedSong>) {
        try {
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_SONGS, json).apply()
        } catch (_: Exception) {}
    }

    private companion object {
        const val PREFS_NAME = "taskly_download_history"
        const val KEY_SONGS = "downloaded_songs_list"
    }
}
