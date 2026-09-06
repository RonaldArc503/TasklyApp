package com.ronaldcolocho.taskly

import com.ronaldcolocho.taskly.domain.util.ChatSearchNormalizer
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchNormalizerTest {
    @Test
    fun normalize_ignoresCaseAndAccents() {
        assertTrue(ChatSearchNormalizer.matches("Conversacion de Jose", "josé"))
    }

    @Test
    fun prefixes_supportPartialPhoneAndWords() {
        val prefixes = ChatSearchNormalizer.prefixes(listOf("Maria Lopez", "+503 7123-4567"))
        assertTrue("mari" in prefixes)
        assertTrue("7123" in prefixes)
    }
}
