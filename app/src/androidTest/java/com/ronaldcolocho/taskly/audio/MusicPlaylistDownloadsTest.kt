package com.ronaldcolocho.taskly.audio

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ronaldcolocho.taskly.domain.model.*
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.domain.repository.IMediaCacheRepository
import com.ronaldcolocho.taskly.media.MediaDownloadManager
import com.ronaldcolocho.taskly.media.MusicDownloadStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class MusicPlaylistDownloadsTest {
    private val context: Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val namespace = "playlist-test-${System.nanoTime()}"
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir() = File(super.getCacheDir(), namespace).apply { mkdirs() }
    }
    private val store = MusicDownloadStore(context)
    private val history = object : IDownloadHistoryRepository {
        override fun getDownloadedSongs() = flowOf(emptyList<DownloadedSong>())
        override suspend fun addDownloadedSong(song: DownloadedSong) = Unit
        override suspend fun removeDownloadedSong(id: String) = Unit
        override suspend fun updateSavedToMessages(id: String, isSaved: Boolean, messageId: String?) = Unit
        override suspend fun clearHistory() = Unit
    }

    @Test fun overlappingListsSkipExistingFilesAndRetryOnlyFailures() = runBlocking {
        val cache = FakeCache(context)
        val existing = track("existing")
        val shared = track("shared")
        val failed = track("failed")
        cache.complete(existing.id)
        cache.fail += failed.id
        val manager = MediaDownloadManager(cache, store, history, context)
        manager.downloadTracks(listOf(existing, shared, failed, shared))
        manager.downloadTracks(listOf(shared, existing))
        waitFor { cache.registered.contains(shared.id) && manager.states.value[failed.id] is MediaDownloadState.Error }
        assertEquals(0, cache.calls[existing.id] ?: 0)
        assertEquals(1, cache.calls[shared.id])
        assertEquals(1, cache.calls[failed.id])
        assertTrue(store.pending().any { it.mediaId == failed.id })
        cache.fail.clear()
        manager.downloadTracks(listOf(existing, shared, failed))
        waitFor { cache.registered.contains(failed.id) && store.pending().none { it.mediaId == failed.id } }
        assertEquals(1, cache.calls[shared.id])
        assertEquals(2, cache.calls[failed.id])
        assertTrue(cache.fileFor(shared.id, MediaKind.AUDIO)!!.length() > 0)
    }

    @Test fun restoresPendingQueueAndKeepsCompletedFiles() = runBlocking {
        val cache = FakeCache(context)
        val complete = track("restart-complete")
        val pending = track("restart-pending")
        cache.complete(complete.id)
        listOf(complete, pending).forEach {
            store.put(CachedAudioReference(it.id, it.url, it.name, "", 0, 0))
        }
        val manager = MediaDownloadManager(cache, store, history, context)
        waitFor { cache.registered.contains(pending.id) && store.pending().none { it.mediaId == pending.id || it.mediaId == complete.id } }
        assertEquals(0, cache.calls[complete.id] ?: 0)
        assertEquals(1, cache.calls[pending.id])
        assertTrue(manager.isDownloaded(complete.id))
    }

    private fun track(id: String) = MusicTrack("playlist-test-$id", "https://example.invalid/$id", id)
    private suspend fun waitFor(predicate: () -> Boolean) = withTimeout(10_000) { while (!predicate()) delay(20) }

    internal class FakeCache(context: Context) : IMediaCacheRepository {
        private val directory = File(context.cacheDir, "playlist-${System.nanoTime()}").apply { mkdirs() }
        val calls = ConcurrentHashMap<String, Int>()
        val fail = ConcurrentHashMap.newKeySet<String>()
        val registered = ConcurrentHashMap.newKeySet<String>()
        fun complete(id: String) { File(directory, id).writeText("audio") }
        override fun isDownloaded(mediaId: String) = File(directory, mediaId).isFile
        override fun fileFor(mediaId: String, kind: MediaKind) = File(directory, mediaId).takeIf { it.isFile }
        override suspend fun refreshFile(mediaId: String, kind: MediaKind) = fileFor(mediaId, kind)
        override suspend fun cacheLocalFile(mediaId: String, sourceFile: File, kind: MediaKind): File? = null
        override fun registerDownloadedAudio(reference: CachedAudioReference) { registered += reference.mediaId }
        override fun registerDownloadedAudios(references: Collection<CachedAudioReference>) = references.forEach(::registerDownloadedAudio)
        override suspend fun downloadedAudioReferences(includeMissing: Boolean) = emptyList<CachedAudioReference>()
        override fun cleanupTempFiles() = Unit
        override fun download(mediaId: String, url: String, kind: MediaKind) = flow {
            calls.merge(mediaId, 1, Int::plus)
            emit(MediaDownloadState.Downloading(0.5f))
            delay(80)
            if (mediaId in fail) emit(MediaDownloadState.Error("interrupted"))
            else { complete(mediaId); emit(MediaDownloadState.Downloaded) }
        }
    }
}
