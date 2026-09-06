package com.ronaldcolocho.taskly.domain.util

import java.text.Normalizer
import java.util.Locale

object ChatSearchNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).trim(),
        Normalizer.Form.NFD
    )
        .replace(COMBINING_MARKS, "")
        .replace(NON_SEARCHABLE, " ")
        .replace(MULTIPLE_SPACES, " ")
        .trim()

    fun matches(value: String, query: String): Boolean {
        val normalizedQuery = normalize(query)
        return normalizedQuery.isNotEmpty() && normalize(value).contains(normalizedQuery)
    }

    fun prefixes(values: Iterable<String>): List<String> = values
        .flatMap { value ->
            val normalized = normalize(value)
            (normalized.split(' ') + normalized)
                .filter { it.length >= MIN_PREFIX_LENGTH }
                .flatMap { token ->
                    (MIN_PREFIX_LENGTH..token.length.coerceAtMost(MAX_PREFIX_LENGTH))
                        .map(token::take)
                }
        }
        .distinct()
        .take(MAX_PREFIX_COUNT)

    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private val NON_SEARCHABLE = "[^a-z0-9@._ -]".toRegex()
    private val MULTIPLE_SPACES = "\\s+".toRegex()
    private const val MIN_PREFIX_LENGTH = 2
    private const val MAX_PREFIX_LENGTH = 32
    private const val MAX_PREFIX_COUNT = 200
}
