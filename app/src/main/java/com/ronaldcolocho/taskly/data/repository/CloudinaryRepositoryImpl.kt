package com.ronaldcolocho.taskly.data.repository

import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.repository.ICloudinaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.buffer
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : ICloudinaryRepository {

    override suspend fun uploadFile(
        file: File,
        mimeType: String,
        uid: String,
        onProgress: ((Int) -> Unit)?
    ): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = mimeType.toMediaType()
            val fileBody = file.asRequestBody(mediaType)
            val progressBody = if (onProgress != null) ProgressRequestBody(fileBody, onProgress) else fileBody

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", "chat_uploads")
                .addFormDataPart("api_key", "811998144818725")
                .addFormDataPart("folder", "taskly/$uid")
                .addFormDataPart("resource_type", "auto")
                .addFormDataPart("file", file.name, progressBody)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/lrpsglzl/auto/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")

            if (!response.isSuccessful) {
                throw Exception("No se pudo subir el archivo.")
            }

            val json = JSONObject(responseBody)

            val resourceType = json.optString("resource_type", "raw")
            val url = json.getString("secure_url")
            val publicId = json.getString("public_id")
            val bytes = json.getLong("bytes")
            val format = json.optString("format", "")

            val width = if (json.has("width")) json.getInt("width") else null
            val height = if (json.has("height")) json.getInt("height") else null
            val duration = if (json.has("duration")) json.getDouble("duration").toInt() else null
            val pages = if (json.has("pages")) json.getInt("pages") else null

            ChatAttachment(
                kind = getKind(mimeType, file.name),
                resourceType = resourceType,
                url = url,
                publicId = publicId,
                name = file.name,
                size = bytes,
                mimeType = mimeType,
                width = width,
                height = height,
                duration = duration,
                pages = pages
            )
        }
    }

    private fun getKind(mimeType: String, name: String): AttachmentKind {
        val t = mimeType.lowercase()
        return when {
            t.startsWith("image/") -> AttachmentKind.IMAGE
            t.startsWith("video/") -> AttachmentKind.VIDEO
            t.startsWith("audio/") || name.matches(Regex(".*\\.(mp3|m4a|aac|ogg|opus|wav|flac)$", RegexOption.IGNORE_CASE)) -> AttachmentKind.AUDIO
            t == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> AttachmentKind.PDF
            t.contains("word") || name.matches(Regex(".*\\.docx?$", RegexOption.IGNORE_CASE)) -> AttachmentKind.DOC
            else -> AttachmentKind.FILE
        }
    }
}

private class ProgressRequestBody(
    private val body: RequestBody,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    private companion object {
        const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 100L
    }

    override fun contentType(): MediaType? = body.contentType()

    override fun contentLength(): Long = body.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = body.contentLength()
        var written = 0L
        var lastProgress = -1
        var lastProgressAt = 0L
        val countingSink = object : Sink {
            override fun write(source: Buffer, byteCount: Long) {
                sink.write(source, byteCount)
                written += byteCount
                if (total > 0) {
                    val progress = ((written * 100) / total).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (progress == 100 ||
                        (progress != lastProgress && now - lastProgressAt >= MIN_PROGRESS_UPDATE_INTERVAL_MS)
                    ) {
                        lastProgress = progress
                        lastProgressAt = now
                        onProgress(progress)
                    }
                }
            }

            override fun flush() = sink.flush()
            override fun close() = sink.close()
            override fun timeout(): okio.Timeout = sink.timeout()
        }
        val counting = countingSink.buffer()
        body.writeTo(counting)
        counting.flush()
    }
}
