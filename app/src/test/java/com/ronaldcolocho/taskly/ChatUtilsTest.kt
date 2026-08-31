package com.ronaldcolocho.taskly

import com.ronaldcolocho.taskly.domain.model.MemberSnapshot
import com.ronaldcolocho.taskly.domain.util.attachmentMediaUrl
import com.ronaldcolocho.taskly.domain.util.extractMentions
import com.ronaldcolocho.taskly.domain.util.findMentionRanges
import com.ronaldcolocho.taskly.domain.util.formatBytes
import com.ronaldcolocho.taskly.domain.util.formatDuration
import com.ronaldcolocho.taskly.domain.util.formatTime
import com.ronaldcolocho.taskly.domain.util.normalizePhone
import com.ronaldcolocho.taskly.domain.util.splitLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUtilsTest {

    @Test
    fun `splitLinks detects urls and trims trailing punctuation`() {
        val segs = splitLinks("Mira https://example.com/path, y adiós")
        assertEquals(3, segs.size)
        assertEquals("Mira ", segs[0].text)
        assertEquals("https://example.com/path", segs[1].url)
        assertEquals("https://example.com/path", segs[1].text)
        assertEquals(" y adiós", segs[2].text)
    }

    @Test
    fun `splitLinks returns single segment when no url`() {
        val segs = splitLinks("sin enlaces")
        assertEquals(1, segs.size)
        assertEquals("sin enlaces", segs[0].text)
    }

    @Test
    fun `extractMentions matches member display names`() {
        val members = mapOf(
            "a" to MemberSnapshot("Juan Pérez", "", ""),
            "b" to MemberSnapshot("Ana", "", "")
        )
        val mentioned = extractMentions("Hola @Juan Pérez revisa", members)
        assertTrue(mentioned.contains("a"))
    }

    @Test
    fun `findMentionRanges returns correct offsets`() {
        val members = mapOf("a" to MemberSnapshot("Juan", "", ""))
        val ranges = findMentionRanges("Hola @Juan cómo estás", members)
        assertEquals(1, ranges.size)
        assertEquals(5, ranges[0].start)
        assertEquals(10, ranges[0].end)
    }

    @Test
    fun `formatTime uses 24h`() {
        // 2024-01-01 13:05 UTC-ish local; just verify pattern HH:mm
        val s = formatTime(1704114300000)
        assertTrue(s.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `formatDuration formats mm ss`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("2:03", formatDuration(123_000))
    }

    @Test
    fun `formatBytes formats units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1048576))
    }

    @Test
    fun `normalizePhone strips country prefix and non digits`() {
        assertEquals("12345678", normalizePhone("503 1234 5678"))
        assertEquals("12345678", normalizePhone("+503 1234-5678"))
        assertEquals("70001111", normalizePhone("7000-1111"))
    }

    @Test
    fun `attachmentMediaUrl applies transformation`() {
        val url = "https://res.cloudinary.com/x/image/upload/v1/taskly/a.jpg"
        val transformed = attachmentMediaUrl(url, "w_700,f_auto,q_auto")
        assertTrue(transformed.contains("/image/upload/w_700,f_auto,q_auto/"))
    }
}
