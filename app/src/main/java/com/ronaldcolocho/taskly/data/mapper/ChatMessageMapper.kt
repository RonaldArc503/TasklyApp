package com.ronaldcolocho.taskly.data.mapper

import com.ronaldcolocho.taskly.data.model.*
import com.ronaldcolocho.taskly.domain.model.*

fun MemberSnapshotDto.toDomain(): MemberSnapshot = MemberSnapshot(
    displayName = this.displayName,
    photoURL = this.photoURL,
    phone = this.phone
)

fun PinnedMessageDto.toDomain(): PinnedMessage = PinnedMessage(
    id = this.id,
    text = this.text,
    senderId = this.senderId,
    pinnedAt = this.pinnedAt
)

fun PinnedMessage.toDto(): PinnedMessageDto = PinnedMessageDto(
    id = this.id,
    text = this.text,
    senderId = this.senderId,
    pinnedAt = this.pinnedAt
)

fun ChatConversationDto.toDomain(currentUserId: String): ChatConversation = ChatConversation(
    id = this.id,
    participantIds = this.participantIds,
    members = this.members.mapValues { it.value.toDomain() },
    unreadCount = this.unread[currentUserId] ?: 0,
    lastMessage = this.lastMessage,
    lastMessageAt = this.lastMessageAt,
    createdAt = this.createdAt,
    isGroup = this.kind == "group" || this.name != null,
    name = this.name,
    pinnedMessages = this.pinnedMessages?.map { it.toDomain() } ?: emptyList()
)

fun ChatAttachmentDto.toDomain(): ChatAttachment = ChatAttachment(
    kind = when(this.kind) {
        "image" -> AttachmentKind.IMAGE
        "video" -> AttachmentKind.VIDEO
        "pdf" -> AttachmentKind.PDF
        "doc" -> AttachmentKind.DOC
        "audio" -> AttachmentKind.AUDIO
        else -> AttachmentKind.FILE
    },
    resourceType = this.resourceType,
    url = this.url,
    publicId = this.publicId,
    name = this.name,
    size = this.size,
    mimeType = this.mimeType,
    width = this.width,
    height = this.height,
    duration = this.duration,
    pages = this.pages
)

fun ReplyInfoDto.toDomain(): ReplyInfo = ReplyInfo(
    id = this.id,
    text = this.text,
    senderId = this.senderId
)

fun ForwardedFromDto.toDomain(): ForwardedFrom = ForwardedFrom(
    uid = this.uid,
    name = this.name
)

fun ChatMessageDto.toDomain(currentUserId: String, isUnread: Boolean = false, isSendingLocally: Boolean = false): ChatMessage {
    val status = when {
        isSendingLocally -> MessageStatus.SENDING
        this.pending -> MessageStatus.SENT
        isUnread -> MessageStatus.DELIVERED
        else -> MessageStatus.READ
    }
    
    return ChatMessage(
        id = this.id,
        senderId = this.senderId,
        text = this.text,
        status = status,
        createdAt = this.createdAt,
        edited = this.edited,
        editedAt = this.editedAt,
        replyTo = this.replyTo?.toDomain(),
        reactions = this.reactions ?: emptyMap(),
        attachments = this.attachments?.map { it.toDomain() } ?: emptyList(),
        mentions = this.mentions ?: emptyList(),
        forwardedFrom = this.forwardedFrom?.toDomain(),
        isSearchIndexed = !this.searchTerms.isNullOrEmpty(),
        conversationId = this.conversationId
    )
}

fun ChatAttachment.toDto(): ChatAttachmentDto = ChatAttachmentDto(
    kind = this.kind.name.lowercase(),
    resourceType = this.resourceType,
    url = this.url,
    publicId = this.publicId,
    name = this.name,
    size = this.size,
    mimeType = this.mimeType,
    width = this.width,
    height = this.height,
    duration = this.duration,
    pages = this.pages
)

fun ReplyInfo.toDto(): ReplyInfoDto = ReplyInfoDto(
    id = this.id,
    text = this.text,
    senderId = this.senderId
)

fun ForwardedFrom.toDto(): ForwardedFromDto = ForwardedFromDto(
    uid = this.uid,
    name = this.name
)

fun ChatMessage.toDto(): ChatMessageDto = ChatMessageDto(
    id = this.id,
    senderId = this.senderId,
    text = this.text,
    createdAt = this.createdAt,
    edited = this.edited,
    editedAt = this.editedAt,
    replyTo = this.replyTo?.toDto(),
    reactions = this.reactions.ifEmpty { null },
    attachments = this.attachments.map { it.toDto() }.ifEmpty { null },
    mentions = this.mentions.ifEmpty { null },
    forwardedFrom = this.forwardedFrom?.toDto(),
    pending = this.status == MessageStatus.SENDING,
    conversationId = this.conversationId
)
