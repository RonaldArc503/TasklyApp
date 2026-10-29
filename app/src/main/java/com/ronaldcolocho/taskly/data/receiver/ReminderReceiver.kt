package com.ronaldcolocho.taskly.data.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ronaldcolocho.taskly.R
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import com.ronaldcolocho.taskly.ui.screen.reminders.ReminderAlarmActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmManager: ReminderAlarmManager
    @Inject lateinit var reminderRepository: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Direct interactive action from notification button: Complete
        if (intent.action == ACTION_COMPLETE) {
            val id = intent.getStringExtra("REMINDER_ID") ?: return
            notificationManager.cancel(id.hashCode())
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    val reminder = reminderRepository.getReminder(uid, id) ?: return@launch
                    if (reminder.repeatType == com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE) {
                        alarmManager.cancel(id)
                        reminderRepository.updateReminder(
                            uid,
                            id,
                            mapOf("isCompleted" to true, "completedAt" to System.currentTimeMillis(), "snoozedUntil" to 0L)
                        )
                    } else {
                        val now = System.currentTimeMillis()
                        val archiveId = "rem-hist-${now.toString(36)}-${java.util.UUID.randomUUID().toString().take(6)}"
                        val periodLabel = com.ronaldcolocho.taskly.domain.model.reminder.reminderTimeLabel(reminder.dueDate, false)
                        val archiveRecord = reminder.copy(
                            id = archiveId,
                            title = "${reminder.title} ($periodLabel)",
                            isCompleted = true,
                            completedAt = now,
                            repeatType = com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType.NONE,
                            snoozedUntil = 0L
                        )
                        runCatching { reminderRepository.addReminder(uid, archiveRecord) }
                        val nextDue = com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrence(
                            reminder.dueDate,
                            reminder.repeatType,
                            reminder.effectiveAnchorDay,
                            now
                        )
                        reminderRepository.updateReminder(
                            uid,
                            id,
                            mapOf(
                                "dueDate" to nextDue,
                                "completedAt" to now,
                                "receipts" to emptyList<Map<String, Any>>(),
                                "snoozedUntil" to 0L
                            )
                        )
                        alarmManager.scheduleReminder(
                            reminder.copy(
                                dueDate = nextDue,
                                receipts = emptyList(),
                                snoozedUntil = 0L
                            )
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 2. Direct interactive action from notification button: Snooze
        if (intent.action == ACTION_SNOOZE) {
            val id = intent.getStringExtra("REMINDER_ID") ?: return
            val snoozeMinutes = intent.getLongExtra("SNOOZE_MINUTES", 15L)
            val snoozeUntil = System.currentTimeMillis() + snoozeMinutes * 60_000L
            notificationManager.cancel(id.hashCode())
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        reminderRepository.updateReminder(uid, id, mapOf("snoozedUntil" to snoozeUntil))
                        val reminder = reminderRepository.getReminder(uid, id)
                        if (reminder != null) {
                            alarmManager.scheduleReminder(reminder.copy(snoozedUntil = snoozeUntil))
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val id = intent.getStringExtra("REMINDER_ID") ?: return
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Tienes un pago pendiente"
        val dueDate = intent.getLongExtra("REMINDER_DUE_DATE", 0L)
        val hasTime = intent.getBooleanExtra("REMINDER_HAS_TIME", false)
        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: TYPE_DUE
        // Approximate fallback alarms may arrive after the main time. Never send a late pre-alert.
        if (alertType != TYPE_DUE && alertType != TYPE_RECOVERY && System.currentTimeMillis() >= dueDate) return

        val channelId = if (alertType == TYPE_DUE) "reminders_alarm_channel" else "reminders_prealert_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                if (alertType == TYPE_DUE) "Alarmas de recordatorios" else "Avisos previos de recordatorios",
                if (alertType == TYPE_DUE) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (alertType == TYPE_DUE) "Avisos de pagos y recordatorios importantes" else "Avisos antes de la alarma principal"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                if (alertType == TYPE_DUE) {
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                    val alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    setSound(alarmSound, audioAttributes)
                }
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
        val canUseFullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

        val isDue = alertType == TYPE_DUE
        val body = reminderMessage(title, alertType)
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isDue) "Alarma de recordatorio" else "Recordatorio próximo")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isDue) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isDue) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(if (isDue) NotificationCompat.DEFAULT_ALL else NotificationCompat.DEFAULT_LIGHTS)
            .setAutoCancel(!isDue)
            .setOngoing(isDue)
            .setContentIntent(fullScreenIntent)

        if (isDue) {
            val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra("REMINDER_ID", id)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                (id + "_complete").hashCode(),
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra("REMINDER_ID", id)
                putExtra("SNOOZE_MINUTES", 15L)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                (id + "_snooze").hashCode(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder
                .addAction(R.drawable.ic_launcher_foreground, "Completar", completePendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, "Posponer 15m", snoozePendingIntent)
        }

        // A full-screen intent is optional presentation. When Android has denied it,
        // keep publishing the normal high-priority notification instead of attaching
        // a full-screen request that the system will reject.
        if (isDue && canUseFullScreenIntent) {
            notificationBuilder.setFullScreenIntent(fullScreenIntent, true)
        }
        val notification = notificationBuilder.build()

        try {
            notificationManager.notify(id.hashCode(), notification)
            android.util.Log.i(
                "ReminderAlarm",
                "Reminder notification posted: id=$id, fullScreenAllowed=$canUseFullScreenIntent"
            )
        } catch (error: SecurityException) {
            android.util.Log.e("ReminderAlarm", "Notification permission denied", error)
        }

        // A normal notification is always posted above. Overlay is only an extra presentation path.
        if (isDue && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))) {
            try {
                context.startActivity(alarmIntent)
            } catch (error: RuntimeException) {
                android.util.Log.e("ReminderAlarm", "Alarm activity could not be opened", error)
            }
        }
    }

    private fun reminderMessage(title: String, type: String): String {
        val name = FirebaseAuth.getInstance().currentUser?.displayName
            ?.trim()?.substringBefore(' ')?.takeIf(String::isNotBlank)
        val greeting = name?.let { "Hola, $it" } ?: "Hola"
        val choices = when (type) {
            TYPE_PRE_10 -> listOf(
                "Recuerda: en 10 minutos tienes \"$title\".",
                "$greeting, en 10 minutos llega \"$title\". Ve preparando el terreno.",
                "Cuenta regresiva: \"$title\" comienza en 10 minutos.",
                "El reloj ya está mirando: faltan 10 minutos para \"$title\"."
            )
            TYPE_PRE_5 -> listOf(
                "$greeting, no lo olvides: \"$title\" es en 5 minutos.",
                "Último tramo: quedan 5 minutos para \"$title\".",
                "Alerta dramática: \"$title\" está a solo 5 minutos.",
                "Cinco minutos. Sí, solo cinco, para \"$title\"."
            )
            TYPE_RECOVERY -> listOf(
                "Tienes pendiente \"$title\" desde que el teléfono estuvo apagado.",
                "$greeting, \"$title\" quedó pendiente mientras el teléfono no estaba disponible.",
                "Recordatorio pendiente: \"$title\" no pudo avisarte a su hora."
            )
            else -> listOf(
                "Es ahora: tienes \"$title\".",
                "$greeting: llegó el momento de \"$title\".",
                "La espera terminó. Es hora de \"$title\".",
                "Alarma activa: no dejes escapar \"$title\"."
            )
        }
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        return choices[(title.hashCode() + day + type.hashCode()).and(0x7fffffff) % choices.size]
    }

    companion object {
        const val ACTION_COMPLETE = "com.ronaldcolocho.taskly.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE = "com.ronaldcolocho.taskly.ACTION_SNOOZE_REMINDER"
        const val EXTRA_ALERT_TYPE = "REMINDER_ALERT_TYPE"
        const val TYPE_PRE_10 = "PRE_10"
        const val TYPE_PRE_5 = "PRE_5"
        const val TYPE_DUE = "DUE"
        const val TYPE_RECOVERY = "RECOVERY"
    }
}
