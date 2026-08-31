package com.ronaldcolocho.taskly.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAY_MS = 86_400_000L

private fun startOfDay(ts: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = ts
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun sameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

fun formatDayLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = Math.round((startOfDay(now) - startOfDay(ts)).toDouble() / DAY_MS)
    return when {
        diff <= 0 -> "Hoy"
        diff == 1L -> "Ayer"
        diff < 7L -> SimpleDateFormat("d MMM", Locale("es", "MX")).format(Date(ts))
        else -> SimpleDateFormat("d MMM yyyy", Locale("es", "MX")).format(Date(ts))
    }
}

fun formatTime(ts: Long): String {
    if (ts <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatLastSeen(ts: Long): String {
    if (ts <= 0) return "Sin conexión"
    val diff = Math.round((System.currentTimeMillis() - ts).toDouble() / DAY_MS)
    return when {
        sameDay(ts, System.currentTimeMillis()) -> "última vez hoy a las ${formatTime(ts)}"
        diff == 1L -> "última vez ayer a las ${formatTime(ts)}"
        else -> {
            val dateFmt = SimpleDateFormat("d MMM", Locale("es", "MX"))
            val yearFmt = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))
            val year = java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.YEAR)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (year == currentYear) "última vez el ${dateFmt.format(Date(ts))} a las ${formatTime(ts)}"
            else "última vez el ${yearFmt.format(Date(ts))}"
        }
    }
}
