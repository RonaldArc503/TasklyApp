package com.ronaldcolocho.taskly.domain.util

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val decimals = if (value >= 100) 0 else 1
    val text = String.format(java.util.Locale.US, "%.${decimals}f", value)
    return "$text ${units[unit]}"
}
