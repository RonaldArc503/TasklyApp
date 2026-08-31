package com.ronaldcolocho.taskly.domain.util

import com.ronaldcolocho.taskly.domain.model.MemberSnapshot

data class MentionRange(
    val uid: String,
    val displayName: String,
    val start: Int,
    val end: Int
)

private val REGEX_SPECIALS = setOf('.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\')

private fun escapeRegExp(s: String): String = buildString {
    for (c in s) {
        if (c in REGEX_SPECIALS) append('\\')
        append(c)
    }
}

private fun namePattern(name: String): String =
    escapeRegExp(name).replace(Regex("\\s+"), "\\\\s+")

fun extractMentions(text: String, members: Map<String, MemberSnapshot>): List<String> {
    val mentioned = mutableListOf<String>()
    for ((uid, m) in members) {
        val name = m.displayName
        if (name.isBlank()) continue
        val regex = Regex("(^|\\s)@${namePattern(name)}(?!\\S)", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(text)) mentioned.add(uid)
    }
    return mentioned
}

fun findMentionRanges(text: String, members: Map<String, MemberSnapshot>): List<MentionRange> {
    val ranges = mutableListOf<MentionRange>()
    for ((uid, m) in members) {
        val name = m.displayName
        if (name.isBlank()) continue
        val regex = Regex("(^|\\s)@${namePattern(name)}(?!\\S)", RegexOption.IGNORE_CASE)
        for (match in regex.findAll(text)) {
            val leading = match.groups[1]?.value.orEmpty()
            val start = match.range.first + leading.length
            ranges.add(MentionRange(uid, name, start, start + 1 + name.length))
        }
    }
    return ranges.sortedBy { it.start }
}
