package com.ronaldcolocho.taskly.ui.screen.reminders

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.domain.model.reminder.reminderTimeLabel
import com.ronaldcolocho.taskly.domain.model.reminder.ReminderRepeatType
import com.ronaldcolocho.taskly.domain.model.reminder.nextReminderOccurrence
import com.ronaldcolocho.taskly.ui.theme.Indigo600
import com.ronaldcolocho.taskly.ui.theme.Slate500
import com.ronaldcolocho.taskly.ui.theme.TasklyTheme
import com.ronaldcolocho.taskly.data.alarm.ReminderAlarmManager
import com.ronaldcolocho.taskly.domain.repository.ReminderRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {
    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmManager: ReminderAlarmManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        showReminder(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showReminder(intent)
    }

    @Deprecated("The reminder must remain visible until the user chooses an action.")
    override fun onBackPressed() = Unit

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
                )
            }
        }
    }

    private fun complete(id: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            val reminder = repository.getReminder(uid, id) ?: return@launch
            if (reminder.repeatType == ReminderRepeatType.NONE) {
                repository.updateReminder(
                    uid,
                    id,
                    mapOf("isCompleted" to true, "completedAt" to System.currentTimeMillis(), "snoozedUntil" to 0L)
                )
                alarmManager.cancel(id)
            } else {
                val next = nextReminderOccurrence(reminder.dueDate, reminder.repeatType)
                repository.updateReminder(uid, id, mapOf("dueDate" to next, "snoozedUntil" to 0L))
                alarmManager.scheduleAlarms(listOf(reminder.copy(dueDate = next, snoozedUntil = 0L)))
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
    onComplete: () -> Unit
) {
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
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
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
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Marcar como completado", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
