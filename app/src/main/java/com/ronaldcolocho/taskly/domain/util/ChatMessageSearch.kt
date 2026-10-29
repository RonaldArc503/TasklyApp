package com.ronaldcolocho.taskly.domain.util

import com.ronaldcolocho.taskly.domain.model.ChatMessage

/** The search and the list must use the same conversation/thread scope and order. */
internal fun messagesInChatScope(messages: List<ChatMessage>, threadRootId: String?): List<ChatMessage> {
    if (threadRootId == null) return messages.filter { it.threadRootId == null }
    val root = messages.firstOrNull { it.id == threadRootId } ?: return emptyList()
    return messages.filter {
        it.id != threadRootId && (it.threadRootId == threadRootId || it.replyTo?.id == threadRootId)
    } + root
}

internal fun searchChatMessages(
    loaded: List<ChatMessage>,
    remote: List<ChatMessage>,
    query: String,
    threadRootId: String?,
    deletedIds: Set<String>
): List<ChatMessage> = messagesInChatScope(
    (remote + loaded).associateBy(ChatMessage::id).values.toList(),
    threadRootId
).filter { message ->
    message.id !in deletedIds && (
        ChatSearchNormalizer.matches(message.text, query) ||
            message.attachments.any { ChatSearchNormalizer.matches(it.name, query) }
        )
}.sortedWith(compareByDescending<ChatMessage> { it.createdAt }.thenBy { it.id })
