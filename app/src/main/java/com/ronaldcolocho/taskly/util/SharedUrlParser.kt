package com.ronaldcolocho.taskly.util

/** Extracts one HTTP(S) URL from text received through Android's share sheet. */
object SharedUrlParser {
    private const val MAX_SHARED_TEXT_LENGTH = 16 * 1024
    private val httpUrl = Regex("""(?i)\bhttps?://[^\s<>"']+""")

    fun extract(sharedText: CharSequence?): String? {
        val text = sharedText?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_SHARED_TEXT_LENGTH) return null

        return httpUrl.find(text)
            ?.value
            ?.trimEnd('.', ',', ';', ':', '!', ')', ']', '}')
            ?.takeIf { it.isNotBlank() }
    }
}
