package com.ronaldcolocho.taskly.data.music

import com.ronaldcolocho.taskly.domain.model.LyricsLine
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Line-level TTML only. External declarations are rejected before invoking an XML parser. */
internal fun parseTtmlLyrics(xml: String): List<LyricsLine> {
    require(xml.length <= 1_048_576 && xml.count { it == '<' } <= 20_000)
    require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    for (feature in listOf("http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd"))
        runCatching { factory.setFeature(feature, false) }
    runCatching { factory.isXIncludeAware = false }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    val root = builder.parse(InputSource(StringReader(xml))).documentElement
    val paragraphs = root.getElementsByTagNameNS("*", "p")
    val offset = root.getElementsByTagNameNS("*", "audio").let { nodes ->
        (nodes.item(0) as? Element)?.getAttribute("lyricOffset")?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
    }
    return (0 until paragraphs.length).take(5000).mapNotNull { index ->
        val p = paragraphs.item(index) as Element
        val begin = p.getAttribute("begin").ifBlank { p.getAttributeNS("http://www.w3.org/ns/ttml#parameter", "begin") }
        val time = ttmlTime(begin) ?: p.getElementsByTagNameNS("*", "span").let { spans ->
            (0 until spans.length).mapNotNull { ttmlTime((spans.item(it) as Element).getAttribute("begin")) }.minOrNull()
        } ?: return@mapNotNull null
        val text = ttmlText(p).replace(Regex("\\s+"), " ").trim()
        text.takeIf { it.isNotBlank() }?.let { LyricsLine((time + offset).coerceAtLeast(0), it) }
    }.groupBy { it.timeMs }.toSortedMap().map { (time, lines) -> LyricsLine(time, lines.map { it.text }.distinct().joinToString("\n")) }
}

private fun ttmlTime(value: String): Long? {
    val parts = value.trim().split(':')
    val seconds = when {
        value.endsWith("ms") -> value.removeSuffix("ms").toDoubleOrNull()?.div(1000)
        value.endsWith("s") -> value.removeSuffix("s").toDoubleOrNull()
        parts.size in 2..3 -> parts.fold(0.0 as Double?) { total, part ->
            if (total == null) null else part.toDoubleOrNull()?.let { total * 60 + it }
        }
        else -> value.trim().toDoubleOrNull()
    }
    return seconds?.takeIf { it.isFinite() && it >= 0 && it <= 86_400 }?.times(1000)?.toLong()
}

private fun ttmlText(node: Node, depth: Int = 0): String {
    require(depth <= 64) { "TTML demasiado anidado" }
    if (node.nodeType == Node.TEXT_NODE) return node.nodeValue.orEmpty()
    if (node is Element) {
        val role = node.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role").ifBlank { node.getAttribute("ttm:role") }
        if (role in listOf("x-translation", "x-roman", "x-bg")) return ""
        if (node.localName == "br") return " "
    }
    return buildString {
        val children = node.childNodes
        for (i in 0 until children.length) append(ttmlText(children.item(i), depth + 1))
    }
}
