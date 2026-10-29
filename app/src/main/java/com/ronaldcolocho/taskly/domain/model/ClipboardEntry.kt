package com.ronaldcolocho.taskly.domain.model

/** A text block saved from the quick copy-and-paste shelf. */
data class ClipboardEntry(
    val id: String,
    val text: String,
    val createdAt: Long,
    val isPermanent: Boolean
)
