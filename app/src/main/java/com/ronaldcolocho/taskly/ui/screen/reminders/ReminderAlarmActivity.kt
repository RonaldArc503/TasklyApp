package com.ronaldcolocho.taskly.ui.screen.reminders

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
import com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrence
import com.ronaldcolocho.taskly.domain.model.reminder.reminderTimeLabel
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import com.ronaldcolocho.taskly.ui.theme.Slate500
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmManager: ReminderAlarmManager

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startAlarmFeedback()
        showReminder(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showReminder(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmFeedback()
    }

    @Deprecated("Allows the user to dismiss the full-screen alarm cleanly.")
    override fun onBackPressed() {
        stopAlarmFeedback()
        finish()
    }

    private fun startAlarmFeedback() {
        runCatching {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }

            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib
            val pattern = longArrayOf(0, 700, 500, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlarmFeedback() {
        runCatching {
            ringtone?.stop()
            vibrator?.cancel()
        }
    }

    private fun showReminder(alarmIntent: Intent) {
        val title = alarmIntent.getStringExtra("REMINDER_TITLE") ?: "Tienes un recordatorio"
        val dueDate = alarmIntent.getLongExtra("REMINDER_DUE_DATE", 0L)
        val hasTime = alarmIntent.getBooleanExtra("REMINDER_HAS_TIME", false)
        val reminderId = alarmIntent.getStringExtra("REMINDER_ID").orEmpty()

        setContent {
            TasklyTheme {
                ReminderAlarmScreen(
                    title = title,
                    dueLabel = reminderTimeLabel(dueDate, hasTime),
                    onComplete = { complete(reminderId) },
                    onSnooze = { minutes -> snooze(reminderId, minutes) },
                    onDismiss = {
                        stopAlarmFeedback()
                        finish()
                    }
                )
            }
        }
    }

    private fun complete(id: String) {
        stopAlarmFeedback()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        lifecycleScope.launch {
            val reminder = repository.getReminder(uid, id) ?: run { finish(); return@launch }
            val now = System.currentTimeMillis()
            if (reminder.repeatType == ReminderRepeatType.NONE) {
                repository.updateReminder(
                    uid,
                    id,
                    mapOf("isCompleted" to true, "completedAt" to now, "snoozedUntil" to 0L)
                )
                alarmManager.cancel(id)
            } else {
                // Archive current period instance
                val archiveId = "rem-hist-${now.toString(36)}-${UUID.randomUUID().toString().take(6)}"
                val periodLabel = reminderTimeLabel(reminder.dueDate, false)
                val archiveRecord = reminder.copy(
                    id = archiveId,
                    title = "${reminder.title} ($periodLabel)",
                    isCompleted = true,
                    completedAt = now,
                    repeatType = ReminderRepeatType.NONE,
                    snoozedUntil = 0L
                )
                runCatching { repository.addReminder(uid, archiveRecord) }

                // Advance active reminder to next cycle
                val nextDue = nextReminderOccurrence(
                    dueDate = reminder.dueDate,
                    repeatType = reminder.repeatType,
                    anchorDay = reminder.effectiveAnchorDay,
                    completedAt = now
                )
                repository.updateReminder(
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
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(id.hashCode())
            finish()
        }
    }

    private fun snooze(id: String, minutes: Long) {
        stopAlarmFeedback()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        lifecycleScope.launch {
            val snoozeUntil = System.currentTimeMillis() + minutes * 60_000L
            repository.updateReminder(uid, id, mapOf("snoozedUntil" to snoozeUntil))
            val reminder = repository.getReminder(uid, id)
            if (reminder != null) {
                alarmManager.scheduleReminder(reminder.copy(snoozedUntil = snoozeUntil))
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(id.hashCode())
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }
}

@Composable
private fun ReminderAlarmScreen(
    title: String,
    dueLabel: String,
    onComplete: () -> Unit,
    onSnooze: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showSnoozeDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF111827), Color(0xFF312E81), Color(0xFF0F172A))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 12.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(Indigo600.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Indigo600,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Recordatorio",
                    color = Indigo600,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = Color(0xFF111827),
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = dueLabel,
                    color = Slate500,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Primary: Complete
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Marcar como completado", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary: Snooze options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSnoozeDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Posponer", fontSize = 13.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Descartar", color = Slate500, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showSnoozeDialog = false },
            title = { Text("Posponer recordatorio", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige cuándo deseas que Taskly te vuelva a avisar:")
                    ListItem(
                        headlineContent = { Text("15 minutos") },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        trailingContent = {
                            TextButton(onClick = { onSnooze(15L); showSnoozeDialog = false }) { Text("Elegir") }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("1 hora") },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        trailingContent = {
                            TextButton(onClick = { onSnooze(60L); showSnoozeDialog = false }) { Text("Elegir") }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Mañana a esta hora") },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        trailingContent = {
                            TextButton(onClick = { onSnooze(24 * 60L); showSnoozeDialog = false }) { Text("Elegir") }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSnoozeDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
