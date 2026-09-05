package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.MediaDownloadState
import com.ronaldcolocho.taskly.domain.model.MediaKind
import kotlinx.coroutines.flow.Flow
import java.io.File

interface IMediaCacheRepository {
    fun isDownloaded(mediaId: String): Boolean
    fun fileFor(mediaId: String, kind: MediaKind): File?
    suspend fun cacheLocalFile(mediaId: String, sourceFile: File, kind: MediaKind): File?

    /**
     * Descarga una sola vez por [mediaId] (single-flight): los llamadores
     * concurrentes reutilizan la misma descarga activa.
     */
    fun download(mediaId: String, url: String, kind: MediaKind): Flow<MediaDownloadState>

    fun cleanupTempFiles()
}
