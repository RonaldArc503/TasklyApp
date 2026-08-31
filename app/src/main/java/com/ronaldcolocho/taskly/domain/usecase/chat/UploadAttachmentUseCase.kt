package com.ronaldcolocho.taskly.domain.usecase.chat

import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.repository.ICloudinaryRepository
import java.io.File
import javax.inject.Inject

class UploadAttachmentUseCase @Inject constructor(
    private val repository: ICloudinaryRepository
) {
    suspend operator fun invoke(
        file: File,
        mimeType: String,
        uid: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<ChatAttachment> {
        return repository.uploadFile(file, mimeType, uid, onProgress)
    }
}
