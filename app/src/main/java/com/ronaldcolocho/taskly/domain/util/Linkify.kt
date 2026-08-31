package com.ronaldcolocho.taskly.domain.util

data class LinkSegment(val text: String, val url: String? = null)

private val URL_RE = Regex("(\\bhttps?://[^\\s<]+)", RegexOption.IGNORE_CASE)
private val TRAILING = Regex("[.,;:!?)\\]}»\"'`]+$")

fun splitLinks(text: String): List<LinkSegment> {
    val segments = mutableListOf<LinkSegment>()
    var lastIndex = 0
    for (match in URL_RE.findAll(text)) {
        val start = match.range.first
        val raw = match.value
        val url = raw.replace(TRAILING, "")
        if (start > lastIndex) {
            segments.add(LinkSegment(text = text.substring(lastIndex, start)))
        }
        segments.add(LinkSegment(text = url, url = url))
        lastIndex = start + raw.length
    }
    if (lastIndex < text.length) {
        segments.add(LinkSegment(text = text.substring(lastIndex)))
    }
    return if (segments.isNotEmpty()) segments else listOf(LinkSegment(text = text))
}
