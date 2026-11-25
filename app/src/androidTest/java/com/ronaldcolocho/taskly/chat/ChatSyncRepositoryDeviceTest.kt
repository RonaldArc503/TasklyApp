package com.ronaldcolocho.taskly.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.ronaldcolocho.taskly.data.repository.ChatRepositoryImpl
import com.ronaldcolocho.taskly.data.repository.DeletedMessagesRepositoryImpl
import com.ronaldcolocho.taskly.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses named Firebase apps and local demo emulators, never the signed-in production account. */
@RunWith(AndroidJUnit4::class)
class ChatSyncRepositoryDeviceTest {
    @Test fun nativeWritesAndLoadedHistoryStayLiveAcrossClients() = runBlocking {
        withTimeout(90_000) {
            val host = InstrumentationRegistry.getArguments().getString("chatEmulatorHost") ?: "10.0.2.2"
            require(host in setOf("10.0.2.2", "127.0.0.1"))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val clients = (1..2).map { index ->
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-taskly-music")
                    .setApiKey("fake-api-key").setApplicationId("1:123:android:chat-test").build(), "chat-$index-${System.nanoTime()}")
                val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9098) }
                val uid = auth.createUserWithEmailAndPassword("chat-${System.nanoTime()}@example.test", "test-password").await().user!!.uid
                val db = FirebaseFirestore.getInstance(app).apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
                    useEmulator(host, 8088)
                }
                Triple(app, uid, db)
            }
            try {
                val (_, alice, aliceDb) = clients[0]
                val (_, bob, bobDb) = clients[1]
                val deletedMessages = DeletedMessagesRepositoryImpl(context)
                val owner = ChatRepositoryImpl(aliceDb, deletedMessages)
                val reader = ChatRepositoryImpl(bobDb, deletedMessages)
                val me = UserProfile(alice, "Alice", phone = "")
                val peer = UserProfile(bob, "Bob", phone = "")
                val ids = coroutineScope { listOf(async { owner.findOrCreateDirectConversation(me, peer).getOrThrow() },
                    async { reader.findOrCreateDirectConversation(peer, me).getOrThrow() }).awaitAll() }
                assertEquals(ids[0], ids[1])
                val id = ids[0]
                val chat = aliceDb.collection("conversations").document(id)
                val state = bobDb.collection("users").document(bob).collection("chatStates").document(id)
                state.set(mapOf("archived" to true, "archivedAt" to 1L, "pinned" to true)).await()
                val first = ChatMessage("native-root", alice, "Native original", MessageStatus.SENT, 100)
                owner.sendMessage(id, alice, first).getOrThrow()
                val received = reader.getRecentMessages(id, bob).first { list -> list.any { it.id == first.id } }
                assertEquals("Native original", received.single().text)
                assertFalse(state.get().await().getBoolean("archived")!!)
                assertTrue(state.get().await().getBoolean("pinned")!!)
                reader.updateReadCursor(id, bob, first.id, 100, clearUnread = true).getOrThrow()
                val receipt = owner.subscribeConversationReceipts(id).first { it[bob]?.lastReadAt == 100L }
                assertEquals(first.id, receipt[bob]?.lastDeliveredMessageId)
                assertEquals(0L, chat.get().await().getLong("unread.$bob"))
                // Old web schema: no conversationId and no global index documents.
                val legacy = chat.collection("messages").document("legacy")
                legacy.set(mapOf("senderId" to alice, "text" to "Old web", "createdAt" to 10L,
                    "formatting" to listOf(mapOf("start" to 0, "end" to 3, "style" to "bold")))).await()
                owner.pinMessage(id, PinnedMessage(legacy.id, "Old web", alice, 1)).getOrThrow()
                owner.editMessage(id, legacy.id, "Edited by native").getOrThrow()
                assertFalse(legacy.get().await().contains("formatting"))
                assertEquals("Edited by native", (chat.get().await().get("pinnedMessages") as List<Map<String, Any>>).single()["text"])
                val index = bobDb.collection("globalChatSearch").whereEqualTo("userId", bob).whereEqualTo("messageId", legacy.id).get().await()
                assertEquals(id, index.documents.single().getString("conversationId"))
                assertTrue(reader.editMessage(id, legacy.id, "Attack").isFailure)
                val history = reader.observeMessagesByIds(id, bob, setOf(legacy.id))
                    .stateIn(scope, SharingStarted.Eagerly, emptyMap())
                history.first { it[legacy.id]?.text == "Edited by native" }
                val batch = aliceDb.batch()
                repeat(35) { i -> batch.set(chat.collection("messages").document("new-$i"),
                    mapOf("senderId" to alice, "text" to "New $i", "createdAt" to 101L + i, "conversationId" to id)) }
                batch.commit().await()
                assertTrue(reader.getRecentMessages(id, bob).first { it.size == 30 }.none { it.id == legacy.id })
                // A second client edits a message outside the latest live page.
                legacy.update(mapOf("text" to "Remote live edit", "edited" to true)).await()
                history.first { it[legacy.id]?.text == "Remote live edit" }
                reader.deleteMessage(id, legacy.id).getOrThrow()
                history.first { it.containsKey(legacy.id) && it[legacy.id] == null }
                assertTrue((chat.get().await().get("pinnedMessages") as List<*>).isEmpty())
                val reply = first.copy(id = "reply", senderId = bob, text = "Reply", threadRootId = first.id, createdAt = 300)
                reader.sendMessage(id, bob, reply).getOrThrow()
                assertEquals(1L, chat.collection("messages").document(first.id).get().await().getLong("replyCount"))
                coroutineScope { listOf(async { owner.deleteMessage(id, reply.id).getOrThrow() },
                    async { reader.deleteMessage(id, reply.id).getOrThrow() }).awaitAll() }
                assertEquals(0L, chat.collection("messages").document(first.id).get().await().getLong("replyCount"))
                val newest = first.copy(id = "latest", text = "Keep newest", createdAt = 500)
                owner.sendMessage(id, alice, newest).getOrThrow()
                reader.deleteMessage(id, first.id).getOrThrow()
                assertEquals("Keep newest", chat.get().await().getString("lastMessage"))
                val noIndex = chat.collection("messages").document("no-index")
                noIndex.set(mapOf("senderId" to alice, "text" to "Old", "createdAt" to 1L)).await()
                reader.deleteMessage(id, noIndex.id).getOrThrow()
                assertFalse(noIndex.get().await().exists())
                val files = List(30) { i -> ChatAttachment(AttachmentKind.PDF, "raw", "https://example.invalid/$i", "file-$i", "$i.pdf", 1, "application/pdf") }
                owner.sendMessage(id, alice, newest.copy(id = "30-files", attachments = files)).getOrThrow()
                assertEquals(30, (chat.collection("messages").document("30-files").get().await().get("attachments") as List<*>).size)

                reader.deleteConversationForMe(bob, id).getOrThrow()
                assertTrue(reader.getRecentMessages(id, bob).first().isEmpty())
                assertTrue(reader.getChatInfoMessages(id, bob, 50).isEmpty())
                assertTrue(reader.getOlderMessages(id, bob, Long.MAX_VALUE, 50).isEmpty())
                assertTrue(reader.getMessageWindowById(id, bob, newest.id, 30).isEmpty())
                assertTrue(reader.searchMessages(id, bob, "keep", 10).isEmpty())
                assertTrue(reader.searchGlobalMessages(bob, "keep", 1).isEmpty())
                assertTrue(reader.getConversation(id, bob).first { it?.isDeletedForMe == true }!!.isDeletedForMe)
                assertTrue(owner.getRecentMessages(id, alice).first { it.isNotEmpty() }.any { it.id == newest.id })

                val incoming = newest.copy(id = "after-private-delete", createdAt = 600)
                owner.sendMessage(id, alice, incoming).getOrThrow()
                assertEquals(listOf(incoming.id), reader.getRecentMessages(id, bob).first { it.isNotEmpty() }.map { it.id })
                assertEquals(listOf(incoming.id), reader.searchGlobalMessages(bob, "keep", 10).map { it.id })
                assertFalse(reader.getConversation(id, bob).first { it?.lastMessageAt == 600L }!!.isDeletedForMe)

                // More than one cleanup page, including messages written by the other participant.
                val oldMessages = aliceDb.batch()
                repeat(51) { i ->
                    oldMessages.set(chat.collection("messages").document("older-page-$i"),
                        mapOf("senderId" to alice, "text" to "Old page", "createdAt" to 400L + i))
                }
                oldMessages.commit().await()
                reader.deleteConversationForEveryone(bob, id).getOrThrow()
                assertTrue(chat.collection("messages").get().await().isEmpty)
                assertTrue(owner.getChatInfoMessages(id, alice, 50).isEmpty())
                assertTrue(owner.getConversation(id, alice).first { it?.isDeletedForMe == true }!!.isDeletedForMe)
                assertFalse(chat.collection("messages").document(incoming.id).get().await().exists())

                val next = newest.copy(id = "after-shared-delete", createdAt = 700)
                owner.sendMessage(id, alice, next).getOrThrow()
                assertEquals(listOf(next.id), reader.getRecentMessages(id, bob).first { it.isNotEmpty() }.map { it.id })

                val attachmentMessage = next.copy(id = "attachment-selection", createdAt = 800, attachments = files.take(4))
                owner.sendMessage(id, alice, attachmentMessage).getOrThrow()
                val privateFile = files[0].referenceIn(attachmentMessage.id)
                val sharedFiles = setOf(files[1], files[3]).mapTo(linkedSetOf()) { it.referenceIn(attachmentMessage.id) }
                reader.deleteAttachments(id, bob, setOf(privateFile), forEveryone = false).getOrThrow()
                assertEquals(files.subList(1, 4), reader.getChatInfoMessages(id, bob, 10).first().attachments)
                val reopened = ChatRepositoryImpl(bobDb, DeletedMessagesRepositoryImpl(context))
                assertEquals(files.subList(1, 4), reopened.getChatInfoMessages(id, bob, 10).first().attachments)
                assertEquals(files.take(4), owner.getChatInfoMessages(id, alice, 10).first().attachments)
                reader.deleteAttachments(id, bob, sharedFiles, forEveryone = true).getOrThrow()
                val retained = owner.getChatInfoMessages(id, alice, 10).first()
                assertEquals(listOf(files[0], files[2]), retained.attachments)
                assertEquals(attachmentMessage.text, retained.text)
                assertEquals(listOf(files[2]), reader.getChatInfoMessages(id, bob, 10).first().attachments)

                val fileOnly = next.copy(id = "file-only", text = "", createdAt = 900, attachments = files.take(1))
                owner.sendMessage(id, alice, fileOnly).getOrThrow()
                reader.deleteAttachments(id, bob, setOf(files.first().referenceIn(fileOnly.id)), forEveryone = true).getOrThrow()
                assertFalse(chat.collection("messages").document(fileOnly.id).get().await().exists())
            } finally {
                scope.cancel()
                clients.forEach { (app, _, db) -> db.terminate().await(); app.delete() }
            }
        }
    }
}
