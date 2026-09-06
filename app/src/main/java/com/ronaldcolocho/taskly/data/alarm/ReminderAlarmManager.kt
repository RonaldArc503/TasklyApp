package com.ronaldcolocho.taskly.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ronaldcolocho.taskly.data.receiver.ReminderReceiver
import com.ronaldcolocho.taskly.domain.model.reminder.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAlarms(reminders: List<Reminder>) {
        val now = System.currentTimeMillis()
        
        reminders.filterNot(Reminder::isCompleted).forEach { reminder ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", reminder.id)
                putExtra("REMINDER_TITLE", reminder.title)
                putExtra("REMINDER_DUE_DATE", reminder.snoozedUntil.takeIf { it > 0L } ?: reminder.dueDate)
                putExtra("REMINDER_HAS_TIME", reminder.hasTime)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = reminder.snoozedUntil.takeIf { it > 0L } ?: reminder.dueDate
            if (reminder.hasTime && triggerAt > now && canScheduleExactAlarms()) {
                // Schedule
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    // The startup permission flow normally prevents this path.
                }
            } else {
                // Cancel if passed or no due date
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    fun cancel(reminderId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
