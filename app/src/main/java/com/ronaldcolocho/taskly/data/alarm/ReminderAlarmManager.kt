package com.ronaldcolocho.taskly.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ronaldcolocho.taskly.data.receiver.ReminderReceiver
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
import com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrenceAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleStore: ReminderScheduleStore
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAlarms(reminders: List<Reminder>) {
        val previousIds = scheduleStore.read().map(Reminder::id).toSet()
        val currentIds = reminders.map(Reminder::id).toSet()
        (previousIds - currentIds).forEach(::cancel)
        scheduleStore.replace(reminders)
        reminders.forEach(::scheduleReminder)
    }

    fun scheduleReminder(reminder: Reminder) {
        cancelAlarmIntents(reminder.id)
        if (reminder.isCompleted || !reminder.hasTime) return
        val triggerAt = reminder.snoozedUntil.takeIf { it > 0L } ?: reminder.dueDate
        if (triggerAt <= System.currentTimeMillis()) return
        scheduleStore.upsert(reminder.copy(dueDate = triggerAt, snoozedUntil = 0L))
        schedule(triggerAt, reminder, ReminderReceiver.TYPE_DUE)
        listOf(10L to ReminderReceiver.TYPE_PRE_10, 5L to ReminderReceiver.TYPE_PRE_5).forEach { (minutes, type) ->
            val preAlertAt = triggerAt - minutes * 60_000L
            if (preAlertAt > System.currentTimeMillis()) schedule(preAlertAt, reminder, type)
        }
    }

    fun rescheduleStoredAlarms() {
        val now = System.currentTimeMillis()
        scheduleStore.read().forEach { reminder ->
            if (reminder.dueDate > now) {
                scheduleReminder(reminder)
            } else {
                // A powered-off phone cannot deliver an alarm. Surface one pending notice and
                // advance recurring schedules to their next real future occurrence.
                postRecovery(reminder)
                if (reminder.repeatType != ReminderRepeatType.NONE) {
                    scheduleReminder(reminder.copy(dueDate = nextReminderOccurrenceAfter(reminder.dueDate, reminder.repeatType, now)))
                } else {
                    cancelAlarmIntents(reminder.id)
                    scheduleStore.remove(reminder.id)
                }
            }
        }
    }

    /** Called by the due receiver before rendering so recurrence never depends on completion. */
    fun advanceRecurringReminder(id: String, occurrenceAt: Long): Reminder? {
        val current = scheduleStore.read().firstOrNull { it.id == id } ?: return null
        if (current.repeatType == ReminderRepeatType.NONE) return current
        val next = nextReminderOccurrenceAfter(occurrenceAt, current.repeatType)
        val advanced = current.copy(dueDate = next, snoozedUntil = 0L)
        scheduleReminder(advanced)
        return advanced
    }

    fun cancel(reminderId: String) {
        cancelAlarmIntents(reminderId)
        scheduleStore.remove(reminderId)
    }

    private fun schedule(triggerAt: Long, reminder: Reminder, type: String) {
        val pendingIntent = reminderReceiverPendingIntent(reminder, type)
        try {
            when {
                canScheduleExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                canScheduleExactAlarms() -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                else -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (error: SecurityException) {
            android.util.Log.e("ReminderAlarm", "Alarm could not be scheduled", error)
        }
    }

    private fun cancelAlarmIntents(reminderId: String) {
        listOf(ReminderReceiver.TYPE_DUE, ReminderReceiver.TYPE_PRE_10, ReminderReceiver.TYPE_PRE_5).forEach { type ->
            alarmManager.cancel(reminderReceiverPendingIntent(Reminder(id = reminderId), type))
        }
    }

    private fun postRecovery(reminder: Reminder) {
        context.sendBroadcast(Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_DUE_DATE", reminder.dueDate)
            putExtra("REMINDER_HAS_TIME", reminder.hasTime)
            putExtra(ReminderReceiver.EXTRA_ALERT_TYPE, ReminderReceiver.TYPE_RECOVERY)
        })
    }

    private fun reminderReceiverPendingIntent(reminder: Reminder, type: String): PendingIntent {
        val triggerAt = reminder.snoozedUntil.takeIf { it > 0L } ?: reminder.dueDate
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_DUE_DATE", triggerAt)
            putExtra("REMINDER_HAS_TIME", reminder.hasTime)
            putExtra(ReminderReceiver.EXTRA_ALERT_TYPE, type)
        }
        return PendingIntent.getBroadcast(
            context,
            "${reminder.id}:$type".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
