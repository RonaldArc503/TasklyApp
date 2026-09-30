package com.ronaldcolocho.taskly.domain.model.reminder

import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime

@Immutable
data class Receipt(
    val publicId: String = "",
    val url: String = "",
    val name: String = "",
    val size: Long = 0L,
    val kind: String = "" // "image" or "pdf"
)

@Immutable
data class Reminder(
    val id: String = "",
    val title: String = "",
    val dueDate: Long = 0L,
    val hasTime: Boolean = false,
    val receipts: List<Receipt> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
    , val isCompleted: Boolean = false
    , val completedAt: Long = 0L
    , val repeatType: ReminderRepeatType = ReminderRepeatType.NONE
    , val snoozedUntil: Long = 0L
) {
    val urgency: Urgency
        get() = urgencyFor(dueDate, hasTime)
}

enum class ReminderRepeatType { NONE, DAILY, WEEKLY, MONTHLY }

fun nextReminderOccurrence(
    dueDate: Long,
    repeatType: ReminderRepeatType,
    completedAt: Long = System.currentTimeMillis()
): Long {
    if (dueDate <= 0L || repeatType == ReminderRepeatType.NONE) return dueDate
    val zone = ZoneId.systemDefault()
    val scheduled = Instant.ofEpochMilli(dueDate).atZone(zone)
    // A manual "Completar ahora" starts the next cycle today, not after a future due date.
    // Keep the chosen time of day for reminders that have one.
    val completed = Instant.ofEpochMilli(completedAt).atZone(zone)
    val next = completed
        .withHour(scheduled.hour)
        .withMinute(scheduled.minute)
        .withSecond(0)
        .withNano(0)
    return when (repeatType) {
        ReminderRepeatType.DAILY -> next.plusDays(1)
        ReminderRepeatType.WEEKLY -> next.plusWeeks(1)
        ReminderRepeatType.MONTHLY -> next.plusMonths(1)
        ReminderRepeatType.NONE -> next
    }.toInstant().toEpochMilli()
}

/** Returns the next calendar occurrence strictly after [after], retaining the selected hour. */
fun nextReminderOccurrenceAfter(
    dueDate: Long,
    repeatType: ReminderRepeatType,
    after: Long = System.currentTimeMillis()
): Long {
    if (dueDate <= 0L || repeatType == ReminderRepeatType.NONE) return dueDate
    val zone = ZoneId.systemDefault()
    var candidate = Instant.ofEpochMilli(dueDate).atZone(zone)
    val boundary = Instant.ofEpochMilli(after).atZone(zone)
    while (!candidate.isAfter(boundary)) {
        candidate = when (repeatType) {
            ReminderRepeatType.DAILY -> candidate.plusDays(1)
            ReminderRepeatType.WEEKLY -> candidate.plusWeeks(1)
            ReminderRepeatType.MONTHLY -> candidate.plusMonths(1)
            ReminderRepeatType.NONE -> candidate
        }
    }
    return candidate.toInstant().toEpochMilli()
}

enum class Urgency {
    OVERDUE, RED, ORANGE, GREEN, NONE
}

fun urgencyFor(
    dueDate: Long,
    hasTime: Boolean = false,
    now: Long = System.currentTimeMillis()
): Urgency {
    if (dueDate <= 0L) return Urgency.NONE
    val days = calendarDaysUntil(dueDate, now)
    if (days < 0 || (hasTime && dueDate < now)) return Urgency.OVERDUE
    if (days <= 3) return Urgency.RED
    if (days <= 7) return Urgency.ORANGE
    return Urgency.GREEN
}

fun urgencyLabel(
    dueDate: Long,
    hasTime: Boolean = false,
    now: Long = System.currentTimeMillis()
): String {
    if (dueDate <= 0L) return "Sin fecha"
    val days = calendarDaysUntil(dueDate, now)
    if (days < 0 || (hasTime && dueDate < now)) {
        if (days == 0) return "Vencido hoy"
        val abs = Math.abs(days)
        return "Vencido hace ${if (abs == 1) "1 día" else "$abs días"}"
    }
    if (days == 0) return "Vence hoy"
    if (days == 1) return "Vence mañana"
    return "Vence en $days días"
}

private fun calendarDaysUntil(dueDate: Long, now: Long): Int {
    val zone = ZoneId.systemDefault()
    val dueDay = Instant.ofEpochMilli(dueDate).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, dueDay).toInt()
}

fun reminderTimeLabel(dueDate: Long, hasTime: Boolean): String {
    if (dueDate <= 0L) return "Sin fecha"
    val format = if (hasTime) "dd/MM/yyyy h:mm a" else "dd/MM/yyyy"
    return java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).format(dueDate)
}

fun nextMonthDueDate(dueDate: Long, now: Long = System.currentTimeMillis()): Long {
    val base = if (dueDate > 0L) dueDate else now
    val cal = Calendar.getInstance()
    cal.timeInMillis = base
    
    val day = cal.get(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.add(Calendar.MONTH, 1)
    
    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, Math.min(day, lastDay))
    
    return cal.timeInMillis
}
