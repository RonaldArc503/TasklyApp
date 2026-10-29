package com.ronaldcolocho.taskly.data.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import com.ronaldcolocho.taskly.domain.model.DownloadedSong
import com.ronaldcolocho.taskly.domain.repository.IDownloadHistoryRepository
import com.ronaldcolocho.taskly.util.DownloadTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class DownloadCompleteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadHistoryRepository: IDownloadHistoryRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val pending = DownloadTracker.getAndRemove(context, downloadId) ?: return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return

        cursor.use { c ->
            if (c.moveToFirst()) {
                val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0 && c.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    var file = File(pending.targetPath)
                    if (!file.exists() || file.length() <= 0L) {
                        val uriIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex >= 0) {
                            val uriStr = c.getString(uriIndex)
                            if (!uriStr.isNullOrBlank()) {
                                val path = android.net.Uri.parse(uriStr).path
                                if (path != null) {
                                    val f = File(path)
                                    if (f.exists() && f.length() > 0) file = f
                                }
                            }
                        }
                    }

                    if (file.exists() && file.length() > 0) {
                        // Keep a permanent copy in internal app storage
                        val internalDir = File(context.filesDir, "Music").apply { if (!exists()) mkdirs() }
                        val internalFile = File(internalDir, file.name)
                        try {
                            file.copyTo(internalFile, overwrite = true)
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadCompleteReceiver", "Failed to backup file to internal storage", e)
                        }
                        val persistentPath = if (internalFile.exists() && internalFile.length() > 0) {
                            internalFile.absolutePath
                        } else {
                            file.absolutePath
                        }

                        val durationSec = extractDurationSeconds(file)
                        val song = DownloadedSong(
                            id = UUID.randomUUID().toString(),
                            title = pending.title,
                            artist = pending.artist,
                            thumbnailUrl = pending.thumbnailUrl,
                            durationSeconds = durationSec,
                            localPath = persistentPath,
                            mimeType = pending.mimeType,
                            sizeBytes = file.length(),
                            downloadedAt = System.currentTimeMillis()
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            downloadHistoryRepository.addDownloadedSong(song)
                        }
                    }
                }
            }
        }
    }

    private fun extractDurationSeconds(file: File): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val sec = (durStr?.toLongOrNull() ?: 0L) / 1000L
            retriever.release()
            sec.toInt()
        } catch (_: Exception) {
            0
        }
    }
}
