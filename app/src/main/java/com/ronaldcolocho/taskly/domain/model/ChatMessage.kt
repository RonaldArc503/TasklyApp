package com.ronaldcolocho.taskly.domain.model

import androidx.compose.runtime.Immutable

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

enum class AttachmentKind {
    IMAGE, VIDEO, PDF, DOC, FILE, AUDIO
}

@Immutable
data class ChatAttachment(
    val kind: AttachmentKind,
    val resourceType: String,
    val url: String,
    val publicId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val pages: Int? = null
)

@Immutable
data class ForwardedFrom(
    val uid: String,
    val name: String
)

@Immutable
data class ReplyInfo(
    val id: String,
    val text: String,
    val senderId: String
)

@Immutable
data class PinnedMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val pinnedAt: Long
)

@Immutable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val status: MessageStatus,
    val createdAt: Long,
    val edited: Boolean = false,
    val editedAt: Long? = null,
    val replyTo: ReplyInfo? = null,
    val reactions: Map<String, String> = emptyMap(),
    val attachments: List<ChatAttachment> = emptyList(),
    val mentions: List<String> = emptyList(),
    val forwardedFrom: ForwardedFrom? = null,
    val isSearchIndexed: Boolean = false,
    val conversationId: String = ""
)

@Immutable
data class ConversationReceipt(
    val userId: String,
    val lastDeliveredAt: Long = 0L,
    val lastDeliveredMessageId: String? = null,
    val lastReadAt: Long = 0L,
    val lastReadMessageId: String? = null
)

@Immutable
data class MemberSnapshot(
    val displayName: String,
    val photoURL: String,
    val phone: String
)

@Immutable
data class MemberDirectory(val map: Map<String, MemberSnapshot>)

@Immutable
data class ChatConversation(
    val id: String,
    val participantIds: List<String>,
    val members: Map<String, MemberSnapshot>,
    val unreadCount: Int,
    val lastMessage: String,
    val lastMessageAt: Long,
    val createdAt: Long,
    val isGroup: Boolean,
    val name: String?,
    val pinnedMessages: List<PinnedMessage> = emptyList(),
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0L
)

@Immutable
data class ConversationUserState(
    val conversationId: String,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L,
    val archived: Boolean = false,
    val archivedAt: Long = 0L
)
