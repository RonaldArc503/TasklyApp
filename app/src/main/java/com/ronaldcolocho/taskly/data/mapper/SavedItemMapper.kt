package com.ronaldcolocho.taskly.data.mapper

import com.ronaldcolocho.taskly.data.model.SavedItemDto
import com.ronaldcolocho.taskly.domain.model.SavedItem

fun SavedItemDto.toDomain(id: String): SavedItem = SavedItem(
    id = id,
    kind = this.kind,
    text = this.text,
    links = this.links,
    attachments = this.attachments.map { it.toDomain() },
    senderId = this.senderId,
    senderName = this.senderName,
    convId = this.convId,
    convName = this.convName,
    createdAt = this.createdAt,
    savedAt = this.savedAt,
    pinned = this.pinned,
    pinnedAt = this.pinnedAt
)

fun SavedItem.toDto(): SavedItemDto = SavedItemDto(
    kind = this.kind,
    text = this.text,
    links = this.links,
    attachments = this.attachments.map { it.toDto() },
    senderId = this.senderId,
    senderName = this.senderName,
    convId = this.convId,
    convName = this.convName,
    createdAt = this.createdAt,
    savedAt = this.savedAt,
    pinned = this.pinned,
    pinnedAt = this.pinnedAt
)
