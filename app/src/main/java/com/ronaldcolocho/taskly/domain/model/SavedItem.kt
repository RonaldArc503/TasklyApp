package com.ronaldcolocho.taskly.domain.model

data class SavedItem(
    val id: String,
    val kind: String,
    val text: String,
    val links: List<String>,
    val attachments: List<ChatAttachment>,
    val senderId: String,
    val senderName: String,
    val convId: String,
    val convName: String,
    val sourceMessageId: String = "",
    val createdAt: Long,
    val savedAt: Long,
    val pinned: Boolean,
    val pinnedAt: Long
)
