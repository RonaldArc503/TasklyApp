package com.ronaldcolocho.taskly.domain.repository

import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import java.io.File

interface ICloudinaryRepository {
    suspend fun uploadFile(
        file: File,
        mimeType: String,
        uid: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<ChatAttachment>
}
