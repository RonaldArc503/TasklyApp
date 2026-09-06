package com.ronaldcolocho.taskly.domain.util

import java.text.Normalizer
import java.util.Locale

object SavedSearchNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).trim(),
        Normalizer.Form.NFD
    ).replace("\\p{M}+".toRegex(), "")

    fun matches(value: String, query: String): Boolean =
        query.isNotBlank() && normalize(value).contains(normalize(query))
}
