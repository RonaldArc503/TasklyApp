package com.ronaldcolocho.taskly

import com.ronaldcolocho.taskly.domain.model.AttachmentKind
import com.ronaldcolocho.taskly.domain.model.ChatAttachment
import com.ronaldcolocho.taskly.domain.model.ChatMessage
import com.ronaldcolocho.taskly.domain.model.MessageStatus
import com.ronaldcolocho.taskly.domain.model.ReplyInfo
import com.ronaldcolocho.taskly.domain.util.messagesInChatScope
import com.ronaldcolocho.taskly.domain.util.searchChatMessages
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageSearchTest {
    private fun message(id: String, text: String = "factura", time: Long = 1L) = ChatMessage(
        id = id, senderId = "user", text = text, status = MessageStatus.SENT, createdAt = time
    )

    @Test fun hiddenThreadRepliesDoNotShiftTheSearchTargetOrAppearAsMainChatResults() {
        val messages = listOf(
            message("newest"),
            message("hidden").copy(threadRootId = "newest"),
            message("target", "recibo")
        )
        val results = searchChatMessages(messages, emptyList(), "recibo", null, emptySet())
        val displayed = messagesInChatScope(messages, null)
        assertEquals(listOf("target"), results.map { it.id })
        assertEquals(1, displayed.indexOfFirst { it.id == results.single().id })
        assertEquals(listOf("newest"), searchChatMessages(messages, emptyList(), "factura", null, emptySet()).map { it.id })
    }

    @Test fun threadSearchContainsOnlyItsRepliesAndOneRoot() {
        val root = message("root").copy(threadRootId = "parent")
        val messages = listOf(
            message("unrelated"),
            message("reply").copy(threadRootId = "root", replyTo = ReplyInfo("root", "", "user")),
            root,
            message("other-thread").copy(threadRootId = "another")
        )
        assertEquals(listOf("reply", "root"), messagesInChatScope(messages, "root").map { it.id })
        assertEquals(setOf("reply", "root"), searchChatMessages(messages, emptyList(), "factura", "root", emptySet()).map { it.id }.toSet())
    }

    @Test fun remotePrefixCandidatesMustMatchTheEntireQuery() {
        val prefix = "a".repeat(32)
        val remote = listOf(message("wrong", prefix + "x"), message("right", prefix + "b"))
        assertEquals(listOf("right"), searchChatMessages(emptyList(), remote, prefix + "b", null, emptySet()).map { it.id })
    }

    @Test fun removedMessagesAreNeverSearchDestinations() {
        val messages = listOf(message("deleted"), message("visible"))
        assertEquals(listOf("visible"), searchChatMessages(messages, messages, "factura", null, setOf("deleted")).map { it.id })
    }

    @Test fun attachmentNamesMatchIgnoringAccentsAndCase() {
        val attachment = ChatAttachment(
            kind = AttachmentKind.PDF, resourceType = "raw", url = "", publicId = "file",
            name = "Cotización Octubre.pdf", size = 0, mimeType = "application/pdf"
        )
        val messages = listOf(message("document", "").copy(attachments = listOf(attachment)))
        assertEquals(listOf("document"), searchChatMessages(messages, emptyList(), "COTIZACION", null, emptySet()).map { it.id })
    }

    @Test fun latestLoadedTextWinsOverStaleRemoteCopiesAndResultsHaveStableOrder() {
        val loaded = listOf(message("edited", "otro texto"), message("b", time = 3), message("a", time = 3))
        val remote = listOf(message("edited"), message("older", time = 2))
        assertEquals(listOf("a", "b", "older"), searchChatMessages(loaded, remote, "factura", null, emptySet()).map { it.id })
    }
}
