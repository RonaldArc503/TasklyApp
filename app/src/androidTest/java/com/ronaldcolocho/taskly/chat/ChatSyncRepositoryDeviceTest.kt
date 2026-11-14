package com.ronaldcolocho.taskly.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.ronaldcolocho.taskly.data.repository.ChatRepositoryImpl
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
                val owner = ChatRepositoryImpl(aliceDb)
                val reader = ChatRepositoryImpl(bobDb)
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
            } finally {
                scope.cancel()
                clients.forEach { (app, _, db) -> db.terminate().await(); app.delete() }
            }
        }
    }
}
