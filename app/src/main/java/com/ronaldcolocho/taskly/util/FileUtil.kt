package com.ronaldcolocho.taskly.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileUtil {

    private const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024

    fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            var name: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
            name
        } catch (_: Exception) {
            null
        }
    }

    fun getSize(context: Context, uri: Uri): Long {
        return try {
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
            size
        } catch (_: Exception) {
            0L
        }
    }

    fun isTooBig(size: Long): Boolean = size > MAX_ATTACHMENT_BYTES

    private val audioNameRegex = Regex(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)$", RegexOption.IGNORE_CASE)
    private val docNameRegex = Regex(".*\\.docx?$", RegexOption.IGNORE_CASE)

    fun classify(mimeType: String?, name: String): AttachmentKind {
        val t = (mimeType ?: "").lowercase()
        return when {
            t.startsWith("image/") -> AttachmentKind.IMAGE
            t.startsWith("video/") -> AttachmentKind.VIDEO
            t.startsWith("audio/") || name.matches(audioNameRegex) -> AttachmentKind.AUDIO
            t == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> AttachmentKind.PDF
            t.contains("word") || name.matches(docNameRegex) -> AttachmentKind.DOC
            else -> AttachmentKind.FILE
        }
    }

    /**
     * Copia el contenido de [uri] a un archivo temporal. Corre en IO: nunca
     * debe bloquear el hilo principal.
     */
    suspend fun getFileFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val name = getDisplayName(context, uri) ?: "temp_file_${System.currentTimeMillis()}"
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val file = File(context.cacheDir, name)
            inputStream.use { ins ->
                FileOutputStream(file).use { outputStream ->
                    ins.copyTo(outputStream)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
