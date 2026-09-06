package com.ronaldcolocho.taskly.data.model



data class MemberSnapshotDto(
    val displayName: String = "",
    val photoURL: String = "",
    val phone: String = ""
)

data class PinnedMessageDto(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val pinnedAt: Long = 0L
)

data class ChatConversationDto(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val members: Map<String, MemberSnapshotDto> = emptyMap(),
    val unread: Map<String, Int> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val createdAt: Long = 0L,
    val kind: String = "dm",
    val name: String? = null,
    val pinnedMessages: List<PinnedMessageDto>? = null
)

data class ChatAttachmentDto(
    val kind: String = "",
    val resourceType: String = "",
    val url: String = "",
    val publicId: String = "",
    val name: String = "",
    val size: Long = 0L,
    val mimeType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val pages: Int? = null
)

data class ReplyInfoDto(
    val id: String = "",
    val text: String = "",
    val senderId: String = ""
)

data class ForwardedFromDto(
    val uid: String = "",
    val name: String = ""
)

data class ChatMessageDto(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val edited: Boolean = false,
    val editedAt: Long? = null,
    val replyTo: ReplyInfoDto? = null,
    val reactions: Map<String, String>? = null,
    val attachments: List<ChatAttachmentDto>? = null,
    val mentions: List<String>? = null,
    val forwardedFrom: ForwardedFromDto? = null,
    val pending: Boolean = false,
    val searchTerms: List<String>? = null,
    val contentKinds: List<String>? = null,
    val conversationId: String = ""
)


