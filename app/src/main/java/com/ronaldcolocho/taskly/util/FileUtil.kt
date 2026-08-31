package com.ronaldcolocho.taskly.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ronaldcolocho.taskly.domain.model.AttachmentKind
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

    fun classify(mimeType: String?, name: String): AttachmentKind {
        val t = (mimeType ?: "").lowercase()
        return when {
            t.startsWith("image/") -> AttachmentKind.IMAGE
            t.startsWith("video/") -> AttachmentKind.VIDEO
            t.startsWith("audio/") || name.matches(Regex(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)$", RegexOption.IGNORE_CASE)) -> AttachmentKind.AUDIO
            t == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> AttachmentKind.PDF
            t.contains("word") || name.matches(Regex(".*\\.docx?$", RegexOption.IGNORE_CASE)) -> AttachmentKind.DOC
            else -> AttachmentKind.FILE
        }
    }

    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val name = getDisplayName(context, uri) ?: "temp_file_${System.currentTimeMillis()}"
            val inputStream = contentResolver.openInputStream(uri) ?: return null
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
