package com.ronaldcolocho.taskly.domain.model.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRepeatTest {
    @Test
    fun dailyCompletionSchedulesTomorrowEvenWhenOriginalDueDateIsLater() {
        val zone = ZoneId.systemDefault()
        val scheduledForLater = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val completedNow = ZonedDateTime.of(2026, 9, 10, 17, 30, 0, 0, zone).toInstant().toEpochMilli()

        val next = nextReminderOccurrence(
            dueDate = scheduledForLater,
            repeatType = ReminderRepeatType.DAILY,
            completedAt = completedNow
        )

        val expected = ZonedDateTime.of(2026, 9, 11, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }

    @Test
    fun dailyOccurrenceAdvancesFromScheduledTimeEvenWhenIgnored() {
        val zone = ZoneId.systemDefault()
        val todayAtNine = ZonedDateTime.of(2026, 9, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val ignoredAtNoon = ZonedDateTime.of(2026, 9, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

        val next = nextReminderOccurrenceAfter(todayAtNine, ReminderRepeatType.DAILY, ignoredAtNoon)

        val expected = ZonedDateTime.of(2026, 9, 11, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }
}
