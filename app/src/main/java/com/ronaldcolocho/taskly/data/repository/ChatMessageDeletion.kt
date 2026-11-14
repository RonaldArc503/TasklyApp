package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

internal fun globalSearchIndexId(uid: String, conversationId: String, messageId: String): String =
    "gcs_" + MessageDigest.getInstance("SHA-256")
        .digest("$uid|$conversationId|$messageId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal suspend fun deleteChatMessage(db: FirebaseFirestore, conversationId: String, messageId: String) {
    val conversation = db.collection("conversations").document(conversationId)
    val messages = conversation.collection("messages")
    val reference = messages.document(messageId)
    val deletedAt = db.runTransaction { tx ->
        val target = tx.get(reference)
        if (!target.exists()) return@runTransaction null
        val chat = tx.get(conversation)
        val rootRef = (target.getString("threadRootId") ?: target.getString("replyTo.id"))?.takeIf { it.isNotBlank() && it != messageId }?.let(messages::document)
        val root = rootRef?.let(tx::get)
        tx.delete(reference)
        (chat.get("participantIds") as? List<String>).orEmpty().forEach { uid ->
            tx.delete(db.collection("globalChatSearch").document(globalSearchIndexId(uid, conversationId, messageId)))
        }
        val count = root?.getLong("replyCount") ?: 0L
        if (rootRef != null && root?.exists() == true && count > 0) tx.update(rootRef, "replyCount", count - 1)
        val pins = (chat.get("pinnedMessages") as? List<Map<String, Any>>).orEmpty()
        if (pins.any { it["id"] == messageId }) tx.update(conversation, "pinnedMessages", pins.filterNot { it["id"] == messageId })
        target.getLong("createdAt") ?: 0L
    }.await() ?: return

    // A concurrent send/edit must win over the preview repair of a deletion.
    val latest = messages.orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get(Source.SERVER).await().documents.firstOrNull()
    db.runTransaction { tx ->
        val chat = tx.get(conversation)
        val candidate = latest?.let { tx.get(it.reference) }
        if (chat.getLong("lastMessageAt") != deletedAt || (candidate != null && !candidate.exists())) return@runTransaction
        val text = candidate?.getString("text").orEmpty()
        val files = (candidate?.get("attachments") as? List<Map<String, Any>>).orEmpty()
        val preview = if (text.isBlank() && files.isNotEmpty()) {
            "📎 ${files.first()["name"] ?: "Adjunto"}${if (files.size > 1) " (+${files.size - 1})" else ""}"
        } else text
        tx.update(conversation, mapOf("lastMessage" to preview, "lastMessageAt" to (candidate?.getLong("createdAt") ?: 0L)))
    }.await()
}
