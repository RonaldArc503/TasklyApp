package com.ronaldcolocho.taskly.data.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ronaldcolocho.taskly.R
import com.ronaldcolocho.taskly.ui.screen.reminders.ReminderAlarmActivity
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
import com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrence
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmManager: ReminderAlarmManager
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("REMINDER_ID") ?: return
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Tienes un pago pendiente"
        val dueDate = intent.getLongExtra("REMINDER_DUE_DATE", 0L)
        val hasTime = intent.getBooleanExtra("REMINDER_HAS_TIME", false)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminder = repository.getReminder(uid, id)
                    if (reminder != null && !reminder.isCompleted && reminder.repeatType != ReminderRepeatType.NONE) {
                        val next = nextReminderOccurrence(reminder.dueDate, reminder.repeatType)
                        repository.updateReminder(uid, id, mapOf("dueDate" to next, "snoozedUntil" to 0L))
                        alarmManager.scheduleAlarms(listOf(reminder.copy(dueDate = next, snoozedUntil = 0L)))
                    }
                } finally { pending.finish() }
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "reminders_alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarmas de recordatorios",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de pagos y recordatorios importantes"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("REMINDER_ID", id)
            putExtra("REMINDER_TITLE", title)
            putExtra("REMINDER_DUE_DATE", dueDate)
            putExtra("REMINDER_HAS_TIME", hasTime)
        }

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarma de recordatorio")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .setContentIntent(fullScreenIntent)
            .build()

        notificationManager.notify(id.hashCode(), notification)
    }
}
