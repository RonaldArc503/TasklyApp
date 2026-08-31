package com.ronaldcolocho.taskly.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.ronaldcolocho.taskly.data.model.ChatAttachmentDto

@IgnoreExtraProperties
data class SavedItemDto(
    val kind: String = "",
    val text: String = "",
    val links: List<String> = emptyList(),
    val attachments: List<ChatAttachmentDto> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val convId: String = "",
    val convName: String = "",
    val createdAt: Long = 0L,
    val savedAt: Long = 0L,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L
)
