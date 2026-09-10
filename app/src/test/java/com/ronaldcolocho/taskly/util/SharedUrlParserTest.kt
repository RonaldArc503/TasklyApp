package com.ronaldcolocho.taskly.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlParserTest {
    @Test
    fun extractsBareUrl() {
        assertEquals("https://youtu.be/abcdefghijk", SharedUrlParser.extract("https://youtu.be/abcdefghijk"))
    }

    @Test
    fun extractsUrlEmbeddedInText() {
        assertEquals(
            "https://www.youtube.com/watch?v=abcdefghijk&feature=share",
            SharedUrlParser.extract("Mira esto https://www.youtube.com/watch?v=abcdefghijk&feature=share")
        )
    }

    @Test
    fun returnsNullForMissingUrlOrEmptyText() {
        assertNull(SharedUrlParser.extract("texto sin enlace"))
        assertNull(SharedUrlParser.extract(""))
    }

    @Test
    fun retainsShortsUrlAndDropsSurroundingPunctuation() {
        assertEquals(
            "https://youtube.com/shorts/abcdefghijk?si=123",
            SharedUrlParser.extract("Ve esto (https://youtube.com/shorts/abcdefghijk?si=123).")
        )
    }
}
